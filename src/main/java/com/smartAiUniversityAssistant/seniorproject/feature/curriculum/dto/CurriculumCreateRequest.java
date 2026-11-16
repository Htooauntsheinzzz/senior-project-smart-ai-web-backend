package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CurriculumCreateRequest(
        @NotNull @Positive Long programId,
        @NotBlank @Size(max = 50) String versionCode,
        @NotBlank @Size(max = 200) String versionName,
        @NotBlank @Size(max = 20) String academicYearLabel,
        @Size(max = 5000) String description,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
