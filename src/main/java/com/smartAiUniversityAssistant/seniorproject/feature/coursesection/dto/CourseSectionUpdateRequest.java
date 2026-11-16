package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import jakarta.validation.constraints.*;

public record CourseSectionUpdateRequest(
        @NotNull @Positive Long courseId,
        @NotBlank @Size(max = 20) String sectionNumber,
        @NotNull @Positive Integer capacity,
        @NotNull @Positive Long lectureId,
        @NotNull @Positive Long semesterId,
        @Positive Long academicTermId,
        @NotBlank String status) {
    public CourseSectionUpdateRequest {
        sectionNumber = sectionNumber == null ? null : sectionNumber.trim();
        status = status == null ? null : status.trim();
    }
}
