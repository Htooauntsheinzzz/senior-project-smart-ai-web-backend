package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.MigrationVersions;
import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import java.util.*;
import java.util.stream.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.*;

/** Step 14A (spec file 45): pgvector extension, ingestion queue columns, pages and chunks tables. */
class RagSchemaMigrationTests extends IntegrationSupport {
    private static final List<String> NEW_TABLES = List.of("academic_document_pages", "academic_document_chunks");
    private String schema;
    private Flyway flyway;
    private JdbcTemplate db;

    @BeforeEach
    void migrate() {
        schema = "rag_" + UUID.randomUUID().toString().replace("-", "");
        flyway = flyway(schema);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(MigrationVersions.count());
        db = jdbc(schema);
    }

    @AfterEach void clean() { flyway.clean(); }

    private static Flyway flyway(String schema) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).cleanDisabled(false).load();
    }

    private static JdbcTemplate jdbc(String schema) {
        // public is deliberately not on the search path: every vector reference in the migrations is schema-qualified.
        return new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @Test
    void newVersionsSucceededAndTablesAreEmpty() {
        assertThat(db.queryForList("SELECT version FROM flyway_schema_history WHERE success AND version::int >= 48 ORDER BY installed_rank", String.class))
                .containsExactlyElementsOf(MigrationVersions.from(48));
        for (String table : NEW_TABLES) {
            assertThat(db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?",
                    Integer.class, schema, table)).as(table).isOne();
            assertThat(db.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
        }
        assertThat(db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=? AND table_name='academic_document_processing'",
                String.class, schema)).contains("failed_stage", "queued_at", "queued_by", "next_attempt_at");
    }

    @Test
    void extensionLivesInPublicAndSurvivesClean() {
        assertThat(extensionSchema()).isEqualTo("public");
        // Two more migrate/clean cycles in their own schemas: cleaning a test schema never drops the extension.
        for (int cycle = 0; cycle < 2; cycle++) {
            String other = "rag_cycle_" + UUID.randomUUID().toString().replace("-", "");
            Flyway second = flyway(other);
            assertThat(second.migrate().migrationsExecuted).isEqualTo(MigrationVersions.count());
            second.clean();
            assertThat(extensionSchema()).isEqualTo("public");
        }
    }

    private String extensionSchema() {
        return db.queryForObject("SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='vector'",
                String.class);
    }

    @Test
    void foreignKeysExistAndNeverCascade() {
        var keys = db.queryForList("""
                SELECT c.conname, c.confdeltype FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace
                WHERE n.nspname=? AND c.contype='f' AND c.conname = ANY(?)
                """, schema, new String[] {"fk_academic_document_pages_file", "fk_academic_document_chunks_file",
                        "fk_academic_document_processing_queued_by"});
        assertThat(keys).hasSize(3);
        assertThat(keys).allSatisfy(key -> assertThat(key.get("confdeltype").toString()).isEqualTo("a"));
    }

    @Test
    void hnswIndexExists() {
        assertThat(db.queryForObject("""
                SELECT am.amname FROM pg_class i JOIN pg_am am ON am.oid=i.relam JOIN pg_namespace n ON n.oid=i.relnamespace
                WHERE n.nspname=? AND i.relname='idx_academic_document_chunks_embedding_hnsw'""", String.class, schema))
                .isEqualTo("hnsw");
        assertThat(db.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=? AND indexname='idx_academic_document_chunks_content_hash'",
                Integer.class, schema)).isOne();
    }

    @Test
    void uniqueAndCheckConstraintsRejectInvalidRows() {
        long[] fixture = fixture();
        long file = fixture[1];
        page(file, 1, "PAGE", "text", null);
        assertThatThrownBy(() -> page(file, 1, "PAGE", "again", null)).hasMessageContaining("uk_academic_document_pages_file_page");
        assertThatThrownBy(() -> page(file, 0, "PAGE", "x", null)).hasMessageContaining("chk_academic_document_pages_number");
        assertThatThrownBy(() -> page(file, 2, "IMAGE", "x", null)).hasMessageContaining("chk_academic_document_pages_unit_type");
        assertThatThrownBy(() -> db.update("INSERT INTO academic_document_pages(file_id,page_number,raw_text,raw_char_count,cleaned_text) VALUES (?,3,'x',1,'x')", file))
                .hasMessageContaining("chk_academic_document_pages_counts");
        assertThatThrownBy(() -> db.update("INSERT INTO academic_document_pages(file_id,page_number,raw_text,raw_char_count) VALUES (?,4,'x',-1)", file))
                .hasMessageContaining("chk_academic_document_pages_counts");
        page(file, 5, "SECTION", "x", "x");

        chunk(file, 0, 10, 1, 1);
        assertThatThrownBy(() -> chunk(file, 0, 10, 1, 1)).hasMessageContaining("uk_academic_document_chunks_file_index");
        assertThatThrownBy(() -> chunk(file, -1, 10, 1, 1)).hasMessageContaining("chk_academic_document_chunks_index");
        assertThatThrownBy(() -> chunk(file, 1, 0, 1, 1)).hasMessageContaining("chk_academic_document_chunks_size");
        assertThatThrownBy(() -> chunk(file, 2, 10, 2, 1)).hasMessageContaining("chk_academic_document_chunks_pages");
        assertThatThrownBy(() -> chunk(file, 3, 10, 0, 1)).hasMessageContaining("chk_academic_document_chunks_pages");
        assertThatThrownBy(() -> db.update("""
                INSERT INTO academic_document_chunks(file_id,chunk_index,content,content_hash,char_count,token_estimate,page_start,page_end,embedding)
                VALUES (?,4,'c',repeat('b',64),1,1,1,1,CAST(? AS public.vector))""", file, vector(768, 0)))
                .hasMessageContaining("chk_academic_document_chunks_embedding");

        assertThatThrownBy(() -> db.update("UPDATE academic_document_processing SET failed_stage='PARSE' WHERE file_id=?", file))
                .hasMessageContaining("chk_academic_document_processing_failed_stage");
        for (String stage : List.of("EXTRACT", "CLEAN", "CHUNK", "EMBED", "INDEX"))
            assertThat(db.update("UPDATE academic_document_processing SET failed_stage=?, queued_by=?, queued_at=now(), next_attempt_at=now() WHERE file_id=?",
                    stage, fixture[0], file)).isOne();
        assertThatThrownBy(() -> db.update("UPDATE academic_document_processing SET queued_by=? WHERE file_id=?", fixture[0] + 999_999, file))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_academic_document_processing_queued_by");
    }

    @Test
    void embeddingAcceptsExactly768Dimensions() {
        long file = fixture()[1];
        embedded(file, 0, vector(768, 0));
        assertThat(db.queryForObject("SELECT public.vector_dims(embedding) FROM academic_document_chunks WHERE chunk_index=0", Integer.class))
                .isEqualTo(768);
        assertThatThrownBy(() -> embedded(file, 1, vector(767, 0))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> embedded(file, 2, vector(769, 0))).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void cosineOrderingWorks() {
        long file = fixture()[1];
        embedded(file, 0, vector(768, 0));            // query cosine 1 → distance 0 is at position 0
        embedded(file, 1, vector(768, 1));            // orthogonal → distance 1
        embedded(file, 2, mixed());                   // cosine 0.7071 → distance 0.2929
        var order = db.queryForList("""
                SELECT chunk_index FROM academic_document_chunks
                ORDER BY embedding OPERATOR(public.<=>) CAST(? AS public.vector) LIMIT 3""", Integer.class, vector(768, 0));
        assertThat(order).containsExactly(0, 2, 1);
    }

    private long[] fixture() {
        long user = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('RAG-1','Rag','Tester','rag@example.test','ACTIVE') RETURNING id""", Long.class);
        long category = db.queryForObject("INSERT INTO document_categories(category_code,category_name) VALUES ('REG','Regulations') RETURNING id", Long.class);
        long document = db.queryForObject("INSERT INTO academic_documents(title,category_id,academic_year_label) VALUES ('T',?,'2026-2027') RETURNING id",
                Long.class, category);
        long file = db.queryForObject("""
                INSERT INTO academic_document_files(document_id,revision_number,original_filename,storage_key,mime_type,
                    file_extension,size_bytes,sha256_checksum,is_current,uploaded_by)
                VALUES (?,1,'a.txt','k1','text/plain','txt',10,repeat('a',64),TRUE,?) RETURNING id""", Long.class, document, user);
        db.update("INSERT INTO academic_document_processing(file_id) VALUES (?)", file);
        return new long[] {user, file};
    }

    private void page(long file, int number, String type, String raw, String cleaned) {
        db.update("INSERT INTO academic_document_pages(file_id,page_number,unit_type,raw_text,raw_char_count,cleaned_text,cleaned_char_count) VALUES (?,?,?,?,?,?,?)",
                file, number, type, raw, raw.length(), cleaned, cleaned == null ? null : cleaned.length());
    }

    private void chunk(long file, int index, int chars, int pageStart, int pageEnd) {
        db.update("""
                INSERT INTO academic_document_chunks(file_id,chunk_index,content,content_hash,char_count,token_estimate,page_start,page_end)
                VALUES (?,?,'content',repeat('c',64),?,?,?,?)""", file, index, chars, Math.max(chars, 1), pageStart, pageEnd);
    }

    private void embedded(long file, int index, String vector) {
        db.update("""
                INSERT INTO academic_document_chunks(file_id,chunk_index,content,content_hash,char_count,token_estimate,page_start,page_end,
                    embedding,embedding_model,embedded_at)
                VALUES (?,?,'c',repeat('d',64),1,1,1,1,CAST(? AS public.vector),'test-model',now())""", file, index, vector);
    }

    /** A unit vector with 1 at the given position. */
    private static String vector(int dimensions, int hot) {
        return IntStream.range(0, dimensions).mapToObj(i -> i == hot ? "1" : "0").collect(Collectors.joining(",", "[", "]"));
    }

    private static String mixed() {
        double v = Math.sqrt(0.5);
        return IntStream.range(0, 768).mapToObj(i -> i < 2 ? Double.toString(v) : "0").collect(Collectors.joining(",", "[", "]"));
    }
}
