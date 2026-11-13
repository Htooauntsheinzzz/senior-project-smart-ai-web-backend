package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * Body of POST /api/v1/admin/knowledge/search; only query is required. Unknown fields are rejected globally.
 * Step 15B: ownProgramQuota (0 to 10, needs studentScope.programId when above 0) and maxNeighborPassages (0 to 4) show
 * exactly what the assistant receives; both default to 0.
 */
public record KnowledgeSearchRequest(@NotNull @Size(max = 500) String query, @Min(1) @Max(10) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double minScore, @Positive Long categoryId, String academicYear,
        @Positive Long documentId, @Valid StudentScope studentScope, @Min(0) @Max(10) Integer ownProgramQuota,
        @Min(0) @Max(4) Integer maxNeighborPassages) {

    /** Spec file 52 call sites: no reserve, no neighbours. */
    public KnowledgeSearchRequest(String query, Integer topK, Double minScore, Long categoryId, String academicYear,
            Long documentId, StudentScope studentScope) {
        this(query, topK, minScore, categoryId, academicYear, documentId, studentScope, null, null);
    }

    /** A preview of what a Student with these values would get; {} is a Student without program and department. */
    public record StudentScope(@Positive Long programId, @Positive Long departmentId) {}
}
