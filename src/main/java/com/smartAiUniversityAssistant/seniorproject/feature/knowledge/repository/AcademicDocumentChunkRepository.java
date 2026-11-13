package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocumentChunk;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AcademicDocumentChunkRepository extends JpaRepository<AcademicDocumentChunk, Long> {
    /** One row of the chunk list: no content and never the vector. */
    interface ChunkSummaryView {
        int getChunkIndex();
        String getSectionTitle();
        int getPageStart();
        int getPageEnd();
        int getCharCount();
        int getTokenEstimate();
        boolean getEmbedded();
        String getPreview();
    }

    /** One chunk without the vector; embedded is computed in SQL. */
    interface ChunkDetailView {
        int getChunkIndex();
        String getSectionTitle();
        int getPageStart();
        int getPageEnd();
        int getCharCount();
        int getTokenEstimate();
        String getContentHash();
        boolean getEmbedded();
        String getEmbeddingModel();
        LocalDateTime getEmbeddedAt();
        String getContent();
    }

    /** A chunk that still needs a vector of the configured model. */
    interface PendingChunkView {
        long getId();
        int getChunkIndex();
        String getSectionTitle();
        String getContent();
        String getContentHash();
        int getTokenEstimate();
    }

    /** The next chunks of a file without a vector of this model, in chunk order, after the given index. */
    @Query(value = """
            SELECT id, chunk_index AS chunkIndex, section_title AS sectionTitle, content, content_hash AS contentHash,
                   token_estimate AS tokenEstimate
            FROM academic_document_chunks
            WHERE file_id = :fileId AND (embedding IS NULL OR embedding_model <> :model) AND chunk_index > :afterIndex
            ORDER BY chunk_index LIMIT :limit""", nativeQuery = true)
    java.util.List<PendingChunkView> findNeedingEmbedding(@Param("fileId") long fileId, @Param("model") String model,
            @Param("afterIndex") int afterIndex, @Param("limit") int limit);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM academic_document_chunks WHERE id IN (SELECT id FROM academic_document_chunks WHERE file_id = :fileId LIMIT :max)",
            nativeQuery = true)
    int deleteBatch(@Param("fileId") long fileId, @Param("max") int max);

    long countByFileId(Long fileId);

    @Query(value = """
            SELECT chunk_index AS chunkIndex, section_title AS sectionTitle, page_start AS pageStart, page_end AS pageEnd,
                   char_count AS charCount, token_estimate AS tokenEstimate, (embedding IS NOT NULL) AS embedded,
                   LEFT(content, 200) AS preview
            FROM academic_document_chunks WHERE file_id = :fileId ORDER BY chunk_index""",
            countQuery = "SELECT COUNT(*) FROM academic_document_chunks WHERE file_id = :fileId", nativeQuery = true)
    Page<ChunkSummaryView> findSummaries(@Param("fileId") long fileId, Pageable pageable);

    @Query(value = """
            SELECT chunk_index AS chunkIndex, section_title AS sectionTitle, page_start AS pageStart, page_end AS pageEnd,
                   char_count AS charCount, token_estimate AS tokenEstimate, content_hash AS contentHash,
                   (embedding IS NOT NULL) AS embedded, embedding_model AS embeddingModel, embedded_at AS embeddedAt, content
            FROM academic_document_chunks WHERE file_id = :fileId AND chunk_index = :chunkIndex""", nativeQuery = true)
    Optional<ChunkDetailView> findDetail(@Param("fileId") long fileId, @Param("chunkIndex") int chunkIndex);
}
