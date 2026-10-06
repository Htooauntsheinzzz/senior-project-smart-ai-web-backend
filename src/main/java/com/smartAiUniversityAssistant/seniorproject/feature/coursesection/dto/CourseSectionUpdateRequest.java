package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import jakarta.validation.constraints.*;

public record CourseSectionUpdateRequest(
        @NotNull @Positive Long courseId,
        @NotBlank @Size(max = 20) String sectionNumber,
        @NotNull @Positive Integer capacity,
        @NotNull @Positive Long lectureId,
        @Size(max = 100) String room,
        @Size(max = 255) String schedule,
        @NotNull @Positive Long semesterId,
        @NotBlank String status) {
    public CourseSectionUpdateRequest {
        sectionNumber = sectionNumber == null ? null : sectionNumber.trim();
        room = room == null || room.isBlank() ? null : room.trim();
        schedule = schedule == null || schedule.isBlank() ? null : schedule.trim();
        status = status == null ? null : status.trim();
    }
}
