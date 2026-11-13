package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

/** State of the vector index (spec file 51, section 11). Never a key or a vector. */
public record KnowledgeIndexStatisticsResponse(boolean ingestionAvailable, boolean embeddingConfigured, String embeddingModel,
        int dimensions, long readyDocuments, long staleDocuments, long searchableDocuments, long totalChunks, long searchableChunks,
        long chunksOfOtherModels, long outdatedChunks, long totalPages, long chunkTableBytes, long vectorIndexBytes) {}
