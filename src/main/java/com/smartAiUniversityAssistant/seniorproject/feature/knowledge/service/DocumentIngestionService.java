package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeDocumentProcessingResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.ReprocessMode;

/**
 * The knowledge ingestion worker (spec file 46). It has no method security and no SecurityContext: it is reached from a
 * controller only through KnowledgeDocumentService, which authorizes the Admin first.
 */
public interface DocumentIngestionService {
    /** One scheduler tick: cancel, recover, queue retries and uploads, then claim and run at most one job. */
    void runOnce();

    /** Queues the current file of a document for processing (reprocess). queuedBy is the authenticated Admin. */
    KnowledgeDocumentProcessingResponse requestProcessing(long documentId, long actorUserId, ReprocessMode mode);

    /** Processing state of the current file of a document; 404 DOCUMENT_FILE_NOT_FOUND when there is none. */
    KnowledgeDocumentProcessingResponse describe(long documentId);

    /** Ingestion is enabled and all five stages are installed. */
    boolean ingestionAvailable();
}
