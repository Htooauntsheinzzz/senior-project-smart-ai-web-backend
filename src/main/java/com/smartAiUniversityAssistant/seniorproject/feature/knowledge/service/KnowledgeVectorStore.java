package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import java.util.Map;

/**
 * The vector store on pgvector (academic_document_chunks.embedding). Native SQL only; vectors are passed as text
 * literals and cast in SQL. Every statement touches at most 500 rows. No method security: callers authorize.
 */
public interface KnowledgeVectorStore {
    /** Copies vectors from other chunks with the same content_hash and model. Returns how many chunks were filled. */
    int reuseEmbeddings(long fileId, String model);

    /** Stores one vector per chunk id. Chunks that already have a vector of this model are left as they are. */
    void saveEmbeddings(Map<Long, float[]> vectorsByChunkId, String model);

    /** Chunk counts of one file; vectors of another model are not counted as embedded. */
    FileIndexCounts countByFile(long fileId, String model);

    /** Deletes at most maxRows chunk rows and at most maxRows page rows of one file (chunks first). Returns the rows deleted. */
    int deleteByFile(long fileId, int maxRows);

    /** Deletes outdated pages and chunks (spec file 51, section 7), at most maxRows rows. Returns the rows deleted. */
    int removeOutdated(int maxRows);

    /** Numbers for the index statistics endpoint. model may be null (embeddings not configured). */
    IndexStatistics statistics(String model);

    /** The nearest eligible chunks (R10, and the filter), closest first. Runs in its own read-only transaction. */
    java.util.List<KnowledgeChunkMatch> search(float[] queryVector, String model, KnowledgeSearchFilter filter, int limit);

    /**
     * Step 15B: the chunks at these positions that are eligible (spec file 52, section 8), ordered by file and chunk
     * index. Runs in its own read-only transaction. An empty list runs no statement. The default exists only for stores
     * that never read neighbours (test fakes of spec file 52).
     */
    default java.util.List<KnowledgeChunkMatch> findChunks(String model, java.util.List<KnowledgeChunkPosition> positions) {
        throw new UnsupportedOperationException("findChunks");
    }
}
