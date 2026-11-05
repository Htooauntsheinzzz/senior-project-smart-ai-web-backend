package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.time.Instant;

/** Processing state of the current revision; ingestionAvailable stays false until the ingestion worker exists. */
public record KnowledgeDocumentProcessingResponse(Long documentId, Long fileId, int revisionNumber, String status,
        int attemptCount, Instant startedAt, Instant completedAt, String lastErrorCode, boolean ingestionAvailable,
        String message) {}
