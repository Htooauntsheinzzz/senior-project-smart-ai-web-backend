package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/** Numbers of the index statistics endpoint (spec file 51, section 11), counted when asked, never stored. */
public record IndexStatistics(long readyDocuments, long staleDocuments, long searchableDocuments, long totalChunks,
        long searchableChunks, long chunksOfOtherModels, long outdatedChunks, long totalPages, long chunkTableBytes,
        long vectorIndexBytes) {}
