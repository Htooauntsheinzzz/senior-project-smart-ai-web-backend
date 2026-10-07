package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import jakarta.validation.constraints.NotBlank;

public record EnrollmentStatusUpdateRequest(@NotBlank String status) {}
