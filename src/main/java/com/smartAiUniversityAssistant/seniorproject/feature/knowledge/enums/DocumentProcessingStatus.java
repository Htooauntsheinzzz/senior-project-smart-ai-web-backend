package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums;

import java.util.*;

/**
 * Ingestion state of one file revision. Only the backend changes it; READY means extraction, chunking and embedding
 * all succeeded, never merely that the upload finished.
 */
public enum DocumentProcessingStatus {
    UPLOADED,
    QUEUED,
    PROCESSING,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED;

    /** Statuses the AI Knowledge Base dashboard counts as "Processing". */
    public static final Set<DocumentProcessingStatus> IN_PROGRESS =
            Collections.unmodifiableSet(EnumSet.of(UPLOADED, QUEUED, PROCESSING, CHUNKING, EMBEDDING));

    public boolean canTransitionTo(DocumentProcessingStatus next) {
        return switch (this) {
            case UPLOADED -> next == QUEUED;
            case QUEUED -> next == PROCESSING;
            case PROCESSING -> next == CHUNKING || next == FAILED;
            case CHUNKING -> next == EMBEDDING || next == FAILED;
            case EMBEDDING -> next == READY || next == FAILED;
            case FAILED -> next == QUEUED;
            case READY -> false;
        };
    }
}
