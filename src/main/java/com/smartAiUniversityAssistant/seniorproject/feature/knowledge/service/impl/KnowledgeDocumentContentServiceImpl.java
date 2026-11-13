package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.AcademicDocumentFile;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeDocumentContentService;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin inspection of extracted pages (spec file 47, section 15). Always the current revision; archived documents are
 * readable, deleted ones are 404. Never returns a storage key or path.
 */
@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
public class KnowledgeDocumentContentServiceImpl implements KnowledgeDocumentContentService {
    private final AcademicDocumentRepository documents;
    private final AcademicDocumentFileRepository files;
    private final AcademicDocumentPageRepository pages;
    private final AcademicDocumentChunkRepository chunks;

    public KnowledgeDocumentContentServiceImpl(AcademicDocumentRepository documents, AcademicDocumentFileRepository files,
            AcademicDocumentPageRepository pages, AcademicDocumentChunkRepository chunks) {
        this.documents = documents;
        this.files = files;
        this.pages = pages;
        this.chunks = chunks;
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public DocumentChunkListResponse chunks(AuthenticatedUser actor, long documentId, DocumentChunkListQuery query) {
        AcademicDocumentFile file = currentFile(actor, documentId);
        var result = chunks.findSummaries(file.getId(), query.pageable());
        return new DocumentChunkListResponse(documentId, file.getId(), file.getRevisionNumber(),
                result.getContent().stream().map(v -> new DocumentChunkSummaryResponse(v.getChunkIndex(), v.getSectionTitle(),
                        v.getPageStart(), v.getPageEnd(), v.getCharCount(), v.getTokenEstimate(), v.getEmbedded(), v.getPreview())).toList(),
                query.page(), query.size(), result.getTotalElements(), result.getTotalPages(),
                query.page() == 0, query.page() >= result.getTotalPages() - 1);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public DocumentChunkResponse chunk(AuthenticatedUser actor, long documentId, long chunkIndex) {
        if (chunkIndex < 0) throw KnowledgeFailure.invalid();
        AcademicDocumentFile file = currentFile(actor, documentId);
        if (chunkIndex > Integer.MAX_VALUE) throw KnowledgeFailure.chunkNotFound();
        var c = chunks.findDetail(file.getId(), (int) chunkIndex).orElseThrow(KnowledgeFailure::chunkNotFound);
        return new DocumentChunkResponse(documentId, file.getId(), file.getRevisionNumber(), c.getChunkIndex(), c.getSectionTitle(),
                c.getPageStart(), c.getPageEnd(), c.getCharCount(), c.getTokenEstimate(), c.getContentHash(), c.getEmbedded(),
                c.getEmbeddingModel(), c.getEmbeddedAt() == null ? null : c.getEmbeddedAt().toInstant(java.time.ZoneOffset.UTC),
                c.getContent());
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public DocumentTextPageListResponse pages(AuthenticatedUser actor, long documentId, DocumentTextPageListQuery query) {
        AcademicDocumentFile file = currentFile(actor, documentId);
        var result = pages.findSummaries(file.getId(), query.pageable());
        return new DocumentTextPageListResponse(documentId, file.getId(), file.getRevisionNumber(),
                result.getContent().stream().map(v -> new DocumentTextPageSummaryResponse(v.getPageNumber(), v.getUnitType(),
                        v.getUnitLabel(), v.getRawCharCount(), v.getCleanedCharCount(), v.getPreview())).toList(),
                query.page(), query.size(), result.getTotalElements(), result.getTotalPages(),
                query.page() == 0, query.page() >= result.getTotalPages() - 1);
    }

    @Override @Transactional(readOnly = true, timeout = 15)
    public DocumentTextPageResponse page(AuthenticatedUser actor, long documentId, long pageNumber) {
        if (pageNumber <= 0) throw KnowledgeFailure.invalid();
        AcademicDocumentFile file = currentFile(actor, documentId);
        if (pageNumber > Integer.MAX_VALUE) throw KnowledgeFailure.pageNotFound();
        var page = pages.findByFileIdAndPageNumber(file.getId(), (int) pageNumber).orElseThrow(KnowledgeFailure::pageNotFound);
        return new DocumentTextPageResponse(documentId, file.getId(), file.getRevisionNumber(), page.getPageNumber(),
                page.getUnitType().name(), page.getUnitLabel(), page.getRawCharCount(), page.getRawText(),
                page.getCleanedCharCount(), page.getCleanedText());
    }

    /** The current revision of a non-deleted document (archived documents included). */
    AcademicDocumentFile currentFile(AuthenticatedUser actor, long documentId) {
        if (actor.forcePasswordChange()) throw new PasswordChangeRequiredException();
        if (documentId <= 0) throw KnowledgeFailure.invalid();
        documents.findByIdAndDeletedFalse(documentId).orElseThrow(KnowledgeFailure::documentNotFound);
        return files.findByDocumentIdAndCurrentTrue(documentId).orElseThrow(KnowledgeFailure::fileNotFound);
    }
}
