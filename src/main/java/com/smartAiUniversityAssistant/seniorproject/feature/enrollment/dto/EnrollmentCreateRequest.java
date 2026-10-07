package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record EnrollmentCreateRequest(
        @NotNull @Positive Long studentId,
        @NotNull @Positive Long courseSectionId,
        LocalDate enrollmentDate,
        @NotBlank String status) {}
