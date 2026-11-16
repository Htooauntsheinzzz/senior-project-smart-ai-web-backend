package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import jakarta.validation.constraints.*;

/** Body of PATCH /api/v1/admin/assistant/feedback/{id}/review (spec file 59). */
public record AdminFeedbackReviewRequest(@NotNull @Pattern(regexp = "PENDING|REVIEWED|RESOLVED") String reviewStatus,
        @Size(max = 1000) String reviewNote) {}
