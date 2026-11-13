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

    /**
     * Allowed status changes (spec file 37 section 9, extended by spec file 46 section 7): QUEUED → FAILED cancels a job
     * whose file was replaced or whose document was deleted; READY → QUEUED is a full reprocess.
     */
    public boolean canTransitionTo(DocumentProcessingStatus next) {
        return switch (this) {
            case UPLOADED -> next == QUEUED;
            case QUEUED -> next == PROCESSING || next == FAILED;
            case PROCESSING -> next == CHUNKING || next == FAILED;
            case CHUNKING -> next == EMBEDDING || next == FAILED;
            case EMBEDDING -> next == READY || next == FAILED;
            case FAILED -> next == QUEUED;
            case READY -> next == QUEUED;
        };
    }

    /** Throws when this change is not allowed; every worker and reprocess status change goes through it. */
    public void requireTransitionTo(DocumentProcessingStatus next) {
        if (!canTransitionTo(next)) throw new IllegalStateException("Invalid processing transition " + this + " -> " + next);
    }
}
