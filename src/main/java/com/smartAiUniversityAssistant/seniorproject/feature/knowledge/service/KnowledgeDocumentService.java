package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeDocumentService {
    KnowledgeDocumentPageResponse search(AuthenticatedUser actor, KnowledgeDocumentListQuery query);
    KnowledgeDocumentResponse getById(AuthenticatedUser actor, long id);
    KnowledgeDocumentResponse updateMetadata(AuthenticatedUser actor, long id, KnowledgeDocumentUpdateRequest request);
    KnowledgeDocumentResponse activate(AuthenticatedUser actor, long id);
    KnowledgeDocumentResponse archive(AuthenticatedUser actor, long id);
    void delete(AuthenticatedUser actor, long id);
    KnowledgeDocumentStatisticsResponse statistics(AuthenticatedUser actor);
    KnowledgeDocumentUploadResponse upload(AuthenticatedUser actor, KnowledgeDocumentUploadRequest request, MultipartFile file);
    KnowledgeDocumentUploadResponse replaceFile(AuthenticatedUser actor, long id, MultipartFile file);
    List<KnowledgeDocumentFileResponse> revisions(AuthenticatedUser actor, long id);
    /** fileId null means the current revision. */
    KnowledgeDocumentDownload download(AuthenticatedUser actor, long id, Long fileId);
    KnowledgeDocumentProcessingResponse processing(AuthenticatedUser actor, long id);
    /** Queues the current file for processing (spec file 46, section 14); 503 while no stage is installed. */
    KnowledgeDocumentProcessingResponse reprocess(AuthenticatedUser actor, long id,
            com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.ReprocessMode mode);
}
