package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeEmbeddingProperties;
import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeSearchProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The vector database of this project: academic_document_chunks.embedding with pgvector and an HNSW cosine index.
 *
 * <p>Rules for every query on the vector column (spec file 51, section 8): cosine distance is {@code <=>} and only
 * {@code ORDER BY embedding <=> CAST(:query AS vector) LIMIT :n} (ascending, with a limit) can use the index; the query
 * vector is a 768-number, L2-normalized text literal cast in SQL; always filter {@code embedding IS NOT NULL} and
 * {@code embedding_model = :model}; with filters, set {@code hnsw.iterative_scan = relaxed_order} and
 * {@code hnsw.ef_search} with SET LOCAL (or set_config(..., true)) inside the query's transaction, never a plain SET, and
 * sort the candidates again in an outer query; do not force the index on small tables; never select the vector.
 * Native SQL only, at most 500 rows per statement, no method security.
 */
@Repository
public class PgVectorKnowledgeVectorStore implements KnowledgeVectorStore {
    static final int BATCH = 500;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    private final int efSearch;

    public PgVectorKnowledgeVectorStore(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactions, Clock clock,
            KnowledgeSearchProperties search) {
        this.efSearch = search.hnswEfSearch();
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setTimeout(15);
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setTimeout(15);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    @Override
    public int reuseEmbeddings(long fileId, String model) {
        int filled = 0;
        long afterId = 0;
        while (true) {
            long after = afterId;
            var rows = jdbc.query("""
                    SELECT id, content_hash FROM academic_document_chunks
                    WHERE file_id = :fileId AND (embedding IS NULL OR embedding_model <> :model) AND id > :after
                    ORDER BY id LIMIT :limit""",
                    new MapSqlParameterSource("fileId", fileId).addValue("model", model).addValue("after", after).addValue("limit", BATCH),
                    (rs, i) -> Map.entry(rs.getLong("id"), rs.getString("content_hash")));
            if (rows.isEmpty()) return filled;
            afterId = rows.get(rows.size() - 1).getKey();
            var params = new MapSqlParameterSource("now", now()).addValue("model", model)
                    .addValue("hashes", rows.stream().map(Map.Entry::getValue).distinct().toList())
                    .addValue("chunkIds", rows.stream().map(Map.Entry::getKey).toList());
            Integer updated = transaction.execute(status -> jdbc.update("""
                    UPDATE academic_document_chunks c
                    SET embedding = s.embedding,
                        embedding_model = s.embedding_model,
                        embedded_at = :now
                    FROM (
                        SELECT DISTINCT ON (o.content_hash) o.content_hash, o.embedding, o.embedding_model
                        FROM academic_document_chunks o
                        WHERE o.embedding IS NOT NULL
                          AND o.embedding_model = :model
                          AND o.content_hash IN (:hashes)
                        ORDER BY o.content_hash, o.id
                    ) s
                    WHERE c.id IN (:chunkIds)
                      AND c.content_hash = s.content_hash
                      AND (c.embedding IS NULL OR c.embedding_model <> :model)""", params));
            filled += updated == null ? 0 : updated;
            if (rows.size() < BATCH) return filled;
        }
    }

    @Override
    public void saveEmbeddings(Map<Long, float[]> vectorsByChunkId, String model) {
        if (vectorsByChunkId.isEmpty()) return;
        if (vectorsByChunkId.size() > BATCH) throw new IllegalArgumentException("At most " + BATCH + " vectors per call");
        LocalDateTime now = now();
        var batch = vectorsByChunkId.entrySet().stream().map(e -> new MapSqlParameterSource("chunkId", e.getKey())
                .addValue("vector", literal(e.getValue())).addValue("model", model).addValue("now", now)).toArray(SqlParameterSource[]::new);
        transaction.executeWithoutResult(status -> jdbc.batchUpdate("""
                UPDATE academic_document_chunks
                SET embedding = CAST(:vector AS vector),
                    embedding_model = :model,
                    embedded_at = :now
                WHERE id = :chunkId
                  AND (embedding IS NULL OR embedding_model <> :model)""", batch));
    }

    @Override
    public FileIndexCounts countByFile(long fileId, String model) {
        var params = new MapSqlParameterSource("fileId", fileId).addValue("model", model);
        return transaction.execute(status -> jdbc.queryForObject("""
                SELECT COUNT(*)                                                                                   AS chunks,
                       COUNT(*) FILTER (WHERE embedding IS NOT NULL AND embedding_model = CAST(:model AS varchar)) AS embedded_with_model,
                       COALESCE(MAX(chunk_index), -1)                                                             AS max_chunk_index,
                       COALESCE(MAX(page_end), 0)                                                                 AS max_page_end,
                       (SELECT COALESCE(MAX(page_number), 0) FROM academic_document_pages WHERE file_id = :fileId) AS page_count
                FROM academic_document_chunks
                WHERE file_id = :fileId""", params, (rs, i) -> new FileIndexCounts(rs.getLong("chunks"),
                rs.getLong("embedded_with_model"), rs.getInt("max_chunk_index"), rs.getInt("max_page_end"), rs.getInt("page_count"))));
    }

    @Override
    public int deleteByFile(long fileId, int maxRows) {
        int deletedChunks = deleteRows("academic_document_chunks", fileId, maxRows);
        int deletedPages = deletedChunks < maxRows ? deleteRows("academic_document_pages", fileId, maxRows) : 0;
        return deletedChunks + deletedPages;
    }

    /** At most maxRows rows of one file and table, in statements of at most 500 rows (one short transaction each). */
    private int deleteRows(String table, long fileId, int maxRows) {
        int deleted = 0;
        while (deleted < maxRows) {
            int limit = Math.min(BATCH, maxRows - deleted);
            Integer n = transaction.execute(status -> jdbc.update("DELETE FROM " + table + " WHERE id IN (SELECT id FROM " + table
                    + " WHERE file_id = :fileId LIMIT :limit)", new MapSqlParameterSource("fileId", fileId).addValue("limit", limit)));
            if (n == null || n == 0) break;
            deleted += n;
            if (n < limit) break;
        }
        return deleted;
    }

    /**
     * Files whose pages and chunks are outdated: every revision of a deleted document, and older revisions of a document
     * whose current revision is READY. Archived documents and older revisions while the new one is not READY are kept.
     */
    static final String OUTDATED_FILE_IDS = """
            SELECT f.id
            FROM academic_document_files f
            JOIN academic_documents d ON d.id = f.document_id
            WHERE (
                    d.is_deleted
                    OR (
                        NOT f.is_current
                        AND EXISTS (
                            SELECT 1
                            FROM academic_document_files cf
                            JOIN academic_document_processing cp ON cp.file_id = cf.id
                            WHERE cf.document_id = f.document_id
                              AND cf.is_current
                              AND cp.status = 'READY'
                        )
                    )
                  )
              AND (
                    EXISTS (SELECT 1 FROM academic_document_chunks c WHERE c.file_id = f.id)
                    OR EXISTS (SELECT 1 FROM academic_document_pages p WHERE p.file_id = f.id)
                  )
            """;
    static final String OUTDATED_FILES = OUTDATED_FILE_IDS + "ORDER BY f.id LIMIT 5";

    @Override
    public int removeOutdated(int maxRows) {
        int deleted = 0;
        while (deleted < maxRows) {
            List<Long> files = jdbc.queryForList(OUTDATED_FILES, Map.of(), Long.class);
            if (files.isEmpty()) break;
            int before = deleted;
            for (long fileId : files) {
                while (deleted < maxRows) {
                    int n = deleteByFile(fileId, Math.min(BATCH, maxRows - deleted));
                    if (n == 0) break;
                    deleted += n;
                }
                if (deleted >= maxRows) break;
            }
            if (deleted == before) break;
        }
        return deleted;
    }

    @Override
    public IndexStatistics statistics(String model) {
        var params = new MapSqlParameterSource("model", model);
        return transaction.execute(status -> jdbc.queryForObject("""
                WITH ready AS (
                    SELECT d.id AS document_id, f.id AS file_id, d.is_active, d.curriculum_version_id,
                           EXISTS (SELECT 1 FROM academic_document_chunks c WHERE c.file_id = f.id
                                   AND (c.embedding IS NULL OR c.embedding_model IS DISTINCT FROM CAST(:model AS varchar))) AS stale
                    FROM academic_documents d
                    JOIN academic_document_files f ON f.document_id = d.id AND f.is_current
                    JOIN academic_document_processing p ON p.file_id = f.id AND p.status = 'READY'
                    WHERE NOT d.is_deleted
                ), searchable AS (
                    SELECT r.* FROM ready r
                    LEFT JOIN curriculum_versions cv ON cv.id = r.curriculum_version_id
                    WHERE r.is_active AND NOT r.stale AND (r.curriculum_version_id IS NULL OR cv.status = 'ACTIVE')
                ), outdated AS (""" + OUTDATED_FILE_IDS + """
                )
                SELECT (SELECT COUNT(*) FROM ready)                                                  AS ready_documents,
                       (SELECT COUNT(*) FROM ready WHERE stale)                                      AS stale_documents,
                       (SELECT COUNT(*) FROM searchable)                                             AS searchable_documents,
                       (SELECT COUNT(*) FROM academic_document_chunks)                               AS total_chunks,
                       (SELECT COUNT(*) FROM academic_document_chunks c JOIN searchable s ON s.file_id = c.file_id) AS searchable_chunks,
                       (SELECT COUNT(*) FROM academic_document_chunks
                        WHERE embedding IS NOT NULL AND embedding_model IS DISTINCT FROM CAST(:model AS varchar)) AS other_models,
                       (SELECT COUNT(*) FROM academic_document_chunks c JOIN outdated o ON o.id = c.file_id) AS outdated_chunks,
                       (SELECT COUNT(*) FROM academic_document_pages)                                AS total_pages,
                       pg_total_relation_size('academic_document_chunks')                           AS chunk_table_bytes,
                       COALESCE(pg_relation_size(to_regclass('idx_academic_document_chunks_embedding_hnsw')), 0) AS vector_index_bytes
                """, params, (rs, i) -> new IndexStatistics(rs.getLong("ready_documents"), rs.getLong("stale_documents"),
                rs.getLong("searchable_documents"), rs.getLong("total_chunks"), rs.getLong("searchable_chunks"),
                rs.getLong("other_models"), rs.getLong("outdated_chunks"), rs.getLong("total_pages"),
                rs.getLong("chunk_table_bytes"), rs.getLong("vector_index_bytes"))));
    }

    /**
     * Spec file 52, section 10. Statement 1 sets the pgvector options for this transaction only (set_config(..., true) is
     * SET LOCAL); statement 2 reads the nearest candidates by cosine distance in an inner query and sorts them again by
     * distance and chunk id. Optional filters are fixed fragments with bind parameters; a missing value leaves its line
     * out (no NULL is ever bound for the scope).
     */
    @Override
    public List<KnowledgeChunkMatch> search(float[] queryVector, String model, KnowledgeSearchFilter filter, int limit) {
        // Both statements run in one read-only transaction, so the SET LOCAL settings apply to the search and end with it.
        return readOnly.execute(status -> searchInTransaction(queryVector, model, filter, limit));
    }

    private List<KnowledgeChunkMatch> searchInTransaction(float[] queryVector, String model, KnowledgeSearchFilter filter, int limit) {
        jdbc.queryForList("SELECT set_config('hnsw.iterative_scan', 'relaxed_order', true), set_config('hnsw.ef_search', :efSearch, true)",
                Map.of("efSearch", Integer.toString(efSearch)));
        var params = new MapSqlParameterSource("queryVector", literal(queryVector)).addValue("model", model).addValue("limit", limit);
        var where = new StringBuilder();
        StudentKnowledgeScope scope = filter == null ? null : filter.studentScope();
        if (scope != null) {
            where.append("\n      AND (\n            (d.curriculum_version_id IS NULL AND d.program_id IS NULL AND d.department_id IS NULL)");
            if (scope.programId() != null) {
                where.append("\n         OR d.program_id = :programId")
                        .append("\n         OR cv.program_id = :programId")
                        .append("\n         OR d.department_id = (SELECT sp.department_id FROM programs sp WHERE sp.id = :programId)");
                params.addValue("programId", scope.programId());
            }
            if (scope.departmentId() != null) {
                where.append("\n         OR d.department_id = :departmentId");
                params.addValue("departmentId", scope.departmentId());
            }
            where.append("\n      )");
        }
        if (filter != null && filter.categoryId() != null) {
            where.append("\n      AND d.category_id = :categoryId");
            params.addValue("categoryId", filter.categoryId());
        }
        if (filter != null && filter.academicYearLabel() != null) {
            where.append("\n      AND d.academic_year_label = :academicYearLabel");
            params.addValue("academicYearLabel", filter.academicYearLabel());
        }
        if (filter != null && filter.documentId() != null) {
            where.append("\n      AND d.id = :documentId");
            params.addValue("documentId", filter.documentId());
        }
        if (filter != null && filter.programDocumentsOnly() != null) {
            where.append("\n      AND (d.program_id = :programDocumentsOnly OR cv.program_id = :programDocumentsOnly)");
            params.addValue("programDocumentsOnly", filter.programDocumentsOnly());
        }
        String sql = """
                WITH candidates AS MATERIALIZED (
                    SELECT c.id AS chunk_id, c.chunk_index, c.section_title, c.page_start, c.page_end, c.content,
                           f.id AS file_id, f.revision_number, f.file_extension,
                           d.id AS document_id, d.title, d.academic_year_label, d.category_id,
                           d.curriculum_version_id, d.program_id, d.department_id, cv.program_id AS curriculum_program_id,
                           c.embedding <=> CAST(:queryVector AS vector) AS distance
                    FROM academic_document_chunks c
                    JOIN academic_document_files f      ON f.id = c.file_id
                    JOIN academic_document_processing p ON p.file_id = f.id
                    JOIN academic_documents d           ON d.id = f.document_id
                    LEFT JOIN curriculum_versions cv    ON cv.id = d.curriculum_version_id
                    WHERE c.embedding IS NOT NULL
                      AND c.embedding_model = :model
                      AND f.is_current
                      AND p.status = 'READY'
                      AND d.is_active
                      AND NOT d.is_deleted
                      AND (d.curriculum_version_id IS NULL OR cv.status = 'ACTIVE')""" + where + """

                    ORDER BY c.embedding <=> CAST(:queryVector AS vector)
                    LIMIT :limit
                )
                SELECT k.*, dc.category_code, dc.category_name
                FROM candidates k
                LEFT JOIN document_categories dc ON dc.id = k.category_id
                ORDER BY k.distance, k.chunk_id""";
        return jdbc.query(sql, params, PgVectorKnowledgeVectorStore::match);
    }

    private static KnowledgeChunkMatch match(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new KnowledgeChunkMatch(rs.getLong("chunk_id"), rs.getInt("chunk_index"),
                rs.getString("section_title"), rs.getInt("page_start"), rs.getInt("page_end"), rs.getString("content"),
                rs.getLong("file_id"), rs.getInt("revision_number"), rs.getString("file_extension"), rs.getLong("document_id"),
                rs.getString("title"), rs.getString("academic_year_label"), (Long) rs.getObject("category_id", Long.class),
                rs.getString("category_code"), rs.getString("category_name"), rs.getObject("curriculum_version_id", Long.class),
                rs.getObject("program_id", Long.class), rs.getObject("department_id", Long.class), rs.getDouble("distance"),
                rs.getObject("curriculum_program_id", Long.class));
    }

    /**
     * Spec file 54, section 8.2: the eligible chunks at the given positions, with every eligibility condition of the
     * search and one fixed fragment per position (bind parameters only). No vector comparison, so distance is 0.
     */
    @Override
    public List<KnowledgeChunkMatch> findChunks(String model, List<KnowledgeChunkPosition> positions) {
        if (positions == null || positions.isEmpty()) return List.of();
        if (positions.size() > BATCH) throw new IllegalArgumentException("Too many positions");
        var params = new MapSqlParameterSource("model", model);
        var terms = new StringJoiner("\n           OR ", "\n      AND (   ", "\n      )");
        for (int i = 0; i < positions.size(); i++) {
            terms.add("(c.file_id = :fileId" + i + " AND c.chunk_index = :chunkIndex" + i + ")");
            params.addValue("fileId" + i, positions.get(i).fileId()).addValue("chunkIndex" + i, positions.get(i).chunkIndex());
        }
        String sql = """
                SELECT c.id AS chunk_id, c.chunk_index, c.section_title, c.page_start, c.page_end, c.content,
                       f.id AS file_id, f.revision_number, f.file_extension,
                       d.id AS document_id, d.title, d.academic_year_label, d.category_id,
                       d.curriculum_version_id, d.program_id, d.department_id, cv.program_id AS curriculum_program_id,
                       dc.category_code, dc.category_name, 0.0 AS distance
                FROM academic_document_chunks c
                JOIN academic_document_files f      ON f.id = c.file_id
                JOIN academic_document_processing p ON p.file_id = f.id
                JOIN academic_documents d           ON d.id = f.document_id
                LEFT JOIN curriculum_versions cv    ON cv.id = d.curriculum_version_id
                LEFT JOIN document_categories dc    ON dc.id = d.category_id
                WHERE c.embedding IS NOT NULL
                  AND c.embedding_model = :model
                  AND f.is_current
                  AND p.status = 'READY'
                  AND d.is_active
                  AND NOT d.is_deleted
                  AND (d.curriculum_version_id IS NULL OR cv.status = 'ACTIVE')""" + terms + """

                ORDER BY c.file_id, c.chunk_index""";
        return readOnly.execute(status -> jdbc.query(sql, params, PgVectorKnowledgeVectorStore::match));
    }

    /** The text literal of a vector: [0.0123,-0.0456,…] written with Float.toString, no spaces. */
    public static String literal(float[] vector) {
        if (vector == null || vector.length != KnowledgeEmbeddingProperties.DIMENSIONS)
            throw new IllegalArgumentException("A vector must have exactly " + KnowledgeEmbeddingProperties.DIMENSIONS + " numbers");
        var b = new StringBuilder(vector.length * 12).append('[');
        double sum = 0;
        for (int i = 0; i < vector.length; i++) {
            if (!Float.isFinite(vector[i])) throw new IllegalArgumentException("A vector must contain finite numbers only");
            sum += (double) vector[i] * vector[i];
            if (i > 0) b.append(',');
            b.append(Float.toString(vector[i]));
        }
        if (sum == 0) throw new IllegalArgumentException("A vector must not be all zeros");
        return b.append(']').toString();
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}
