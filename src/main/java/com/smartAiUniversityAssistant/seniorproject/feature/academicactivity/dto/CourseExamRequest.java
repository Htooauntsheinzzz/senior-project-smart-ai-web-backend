package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto;

import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;

// roomId is accepted for the future Room module; until a rooms table exists any non-null value is rejected.
public record CourseExamRequest(@NotNull @Positive Long courseSectionId, @NotBlank @Size(max = 255) String title,
        @NotBlank String examType, @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt,
        Long roomId, @Size(max = 150) String roomLabel) {
    public CourseExamRequest {
        title = title == null ? null : title.trim();
        roomLabel = roomLabel == null || roomLabel.isBlank() ? null : roomLabel.trim();
    }
}
