package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;

/**
 * One step of the ingestion pipeline (spec file 46, section 6). Implementations are Spring beans, exactly one per type.
 * A stage reads its input from PostgreSQL or file storage, writes its output in short transactions, is repeatable,
 * never changes the processing status, has no method security, throws only KnowledgeIngestionException and never
 * catches what IngestionJobControl.checkpoint() throws.
 */
public interface IngestionStage {
    IngestionStageType type();
    void execute(IngestionJob job, IngestionJobControl control);
}
