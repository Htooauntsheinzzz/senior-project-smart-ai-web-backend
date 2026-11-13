package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/**
 * Given to a stage by the worker. A stage calls checkpoint() between pages, batches or provider calls, at least once per
 * minute of work. It heartbeats the job and stops the run (by an unchecked exception only the worker understands) when
 * the job was lost, cancelled or ran past the stage timeout.
 */
public interface IngestionJobControl {
    void checkpoint();
}
