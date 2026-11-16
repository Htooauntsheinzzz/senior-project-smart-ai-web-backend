package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * One AI Knowledge Base row. scope.type tells which reference is stored (CURRICULUM, PROGRAM, DEPARTMENT or
 * UNIVERSITY); the program and department are resolved through the curriculum or program when not stored directly.
 * file, uploadedBy and processingStatus describe the current revision.
 */
public record KnowledgeDocumentResponse(Long id, String title, String description, Category category,
        String academicYearLabel, Scope scope, FileSummary file, Uploader uploadedBy, Instant uploadedAt,
        String processingStatus, @JsonProperty("isActive") boolean isActive,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {

    public record Category(Long id, String code, String name, @JsonProperty("isActive") boolean isActive) {}

    public record Scope(String type, Long curriculumVersionId, String curriculumVersionCode, String curriculumStatus,
            Long programId, String programName, Long departmentId, String departmentName) {}

    public record FileSummary(Long id, String originalFilename, String extension, String mimeType, long sizeBytes,
            int revisionNumber) {}

    public record Uploader(Long id, String displayName) {}
}
