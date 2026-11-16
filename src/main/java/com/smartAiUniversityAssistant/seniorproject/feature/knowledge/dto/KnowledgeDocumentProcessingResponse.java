package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.time.Instant;

/**
 * Processing state of the current revision. ingestionAvailable is true when ingestion is enabled and all five stages
 * are installed. Page and chunk counts are derived from the pages and chunks tables, never stored; embeddedChunkCount counts
 * vectors of the configured model only, and indexStale is true for a READY file that lacks one (spec file 51, section 9).
 */
public record KnowledgeDocumentProcessingResponse(Long documentId, Long fileId, int revisionNumber, String status,
        int attemptCount, Instant startedAt, Instant completedAt, String lastErrorCode, boolean ingestionAvailable,
        String message, String failedStage, String lastErrorMessage, Instant queuedAt, Instant nextAttemptAt,
        long pageCount, long chunkCount, long embeddedChunkCount, boolean indexStale) {}
