package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

/** Read-only Admin inspection of what the pipeline produced for the current revision of a document. */
public interface KnowledgeDocumentContentService {
    DocumentTextPageListResponse pages(AuthenticatedUser actor, long documentId, DocumentTextPageListQuery query);
    DocumentTextPageResponse page(AuthenticatedUser actor, long documentId, long pageNumber);
    DocumentChunkListResponse chunks(AuthenticatedUser actor, long documentId, DocumentChunkListQuery query);
    DocumentChunkResponse chunk(AuthenticatedUser actor, long documentId, long chunkIndex);
}
