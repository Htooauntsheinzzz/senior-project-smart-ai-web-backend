package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto;

import jakarta.validation.constraints.*;

// correctionReason is mandatory when the result is already FINALIZED.
public record CourseResultUpdateRequest(@NotNull @Positive Long gradeDefinitionId, @Size(max = 500) String correctionReason) {
    public CourseResultUpdateRequest {
        correctionReason = correctionReason == null || correctionReason.isBlank() ? null : correctionReason.trim();
    }
}
