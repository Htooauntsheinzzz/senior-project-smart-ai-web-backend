package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import jakarta.validation.constraints.*;

/** Multipart text fields of the upload form; the file arrives separately. Audit fields are never accepted. */
public record KnowledgeDocumentUploadRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 20) String academicYearLabel,
        @Size(max = 5000) String description,
        @Positive Long curriculumVersionId,
        @Positive Long programId,
        @Positive Long departmentId) {}
