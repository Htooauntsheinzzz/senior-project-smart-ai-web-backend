package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

public record CourseAssignmentRequest(@NotNull @Positive Long courseSectionId, @NotBlank @Size(max = 255) String title,
        @Size(max = 10000) String description, @NotNull OffsetDateTime dueAt) {
    public CourseAssignmentRequest {
        title = title == null ? null : title.trim();
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
