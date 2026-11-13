package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;

/**
 * Thrown by an ingestion stage. Never reaches an HTTP response: the worker stores code and message on the processing
 * row. The message is a fixed safe text and never contains document text, a file name, a path or a provider body.
 */
public class KnowledgeIngestionException extends RuntimeException {
    private final IngestionStageType stage;
    private final String code;
    private final boolean retryable;

    public KnowledgeIngestionException(IngestionStageType stage, String code, boolean retryable, String safeMessage) {
        super(safeMessage, null, false, false);
        this.stage = stage;
        this.code = code;
        this.retryable = retryable;
    }

    public IngestionStageType stage() { return stage; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }

    /** The worker thread was interrupted (application shutdown); the job is retried later. */
    public static KnowledgeIngestionException interrupted(IngestionStageType stage) {
        return new KnowledgeIngestionException(stage, "INGESTION_INTERRUPTED", true, "Processing was interrupted.");
    }
}
