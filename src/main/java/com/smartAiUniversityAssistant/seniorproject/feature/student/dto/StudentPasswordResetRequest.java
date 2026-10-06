package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import jakarta.validation.constraints.NotBlank;

public record StudentPasswordResetRequest(
        @NotBlank String temporaryPassword,
        @NotBlank String confirmPassword) {
    @Override public String toString() { return "StudentPasswordResetRequest[REDACTED]"; }
}
