package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

/** One row of the chunk list; never the vector. */
public record DocumentChunkSummaryResponse(int chunkIndex, String sectionTitle, int pageStart, int pageEnd, int charCount,
        int tokenEstimate, boolean embedded, String preview) {}
