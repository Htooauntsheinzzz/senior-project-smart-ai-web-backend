package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums;

/** The five pipeline stages in execution order, each with the processing status shown while it runs (spec file 46). */
public enum IngestionStageType {
    EXTRACT(DocumentProcessingStatus.PROCESSING),
    CLEAN(DocumentProcessingStatus.PROCESSING),
    CHUNK(DocumentProcessingStatus.CHUNKING),
    EMBED(DocumentProcessingStatus.EMBEDDING),
    INDEX(DocumentProcessingStatus.EMBEDDING);

    private final DocumentProcessingStatus status;

    IngestionStageType(DocumentProcessingStatus status) { this.status = status; }

    /** The processing status shown while this stage runs. */
    public DocumentProcessingStatus status() { return status; }
}
