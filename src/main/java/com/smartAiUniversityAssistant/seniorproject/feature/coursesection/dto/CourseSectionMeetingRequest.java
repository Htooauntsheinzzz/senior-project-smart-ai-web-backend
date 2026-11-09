package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.*;

// roomId is a campus room (spec file 42). active=true publishes the meeting, which requires roomId; roomLabel is legacy text.
public record CourseSectionMeetingRequest(
        @NotBlank String meetingType,
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @NotNull LocalDate effectiveFrom,
        @NotNull LocalDate effectiveTo,
        @Positive Long roomId,
        @Size(max = 150) String roomLabel,
        Boolean active) {
    public CourseSectionMeetingRequest {
        roomLabel = roomLabel == null || roomLabel.isBlank() ? null : roomLabel.trim();
    }
}
