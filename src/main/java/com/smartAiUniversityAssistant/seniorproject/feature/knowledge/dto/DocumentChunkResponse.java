package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.time.Instant;

/** One chunk of the current revision; the vector itself is never returned. */
public record DocumentChunkResponse(long documentId, long fileId, int revisionNumber, int chunkIndex, String sectionTitle,
        int pageStart, int pageEnd, int charCount, int tokenEstimate, String contentHash, boolean embedded,
        String embeddingModel, Instant embeddedAt, String content) {}
