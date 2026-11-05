package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import jakarta.validation.constraints.*;

/** Full metadata replacement; all scope fields null makes the document university-wide. */
public record KnowledgeDocumentUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 20) String academicYearLabel,
        @Positive Long curriculumVersionId,
        @Positive Long programId,
        @Positive Long departmentId) {}
