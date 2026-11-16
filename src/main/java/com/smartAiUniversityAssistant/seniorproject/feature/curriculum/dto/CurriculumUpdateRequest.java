package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** The owning program is fixed at creation; documents and the one-active rule depend on it. */
public record CurriculumUpdateRequest(
        @NotBlank @Size(max = 50) String versionCode,
        @NotBlank @Size(max = 200) String versionName,
        @NotBlank @Size(max = 20) String academicYearLabel,
        @Size(max = 5000) String description,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
