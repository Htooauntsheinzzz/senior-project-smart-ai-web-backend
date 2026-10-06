package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import jakarta.validation.constraints.NotBlank;

public record StudentChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    @Override public String toString() { return "StudentChangePasswordRequest[REDACTED]"; }
}
