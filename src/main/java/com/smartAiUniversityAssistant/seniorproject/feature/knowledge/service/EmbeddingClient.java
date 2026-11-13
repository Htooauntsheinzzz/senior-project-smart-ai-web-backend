package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import java.util.List;

/**
 * Turns text into L2-normalized vectors of dimensions() numbers (spec file 50, section 6). Never logs text, vectors or
 * keys. Failures are EmbeddingException; an empty list, too many texts or a blank text is IllegalArgumentException.
 * Documents and questions have separate methods so a provider-specific client can add task hints later.
 */
public interface EmbeddingClient {
    boolean configured();
    /** Stored in academic_document_chunks.embedding_model. */
    String model();
    int dimensions();
    /** One vector per text, same order. */
    List<float[]> embedDocuments(List<String> texts);
    /** Used by semantic search (spec file 52): the query timeout and at most one retry. */
    float[] embedQuery(String text);
}
