package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/** Finds stored files that no metadata row references (an upload interrupted between storage and commit). */
public interface KnowledgeStorageReconciliationService {
    /** Returns the number of orphaned files found; they are deleted only when deleteOrphans is enabled. */
    int reconcile();
}
