package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import jakarta.validation.constraints.NotBlank;

public record StudentRefreshRequest(@NotBlank String refreshToken) {
    @Override public String toString() { return "StudentRefreshRequest[REDACTED]"; }
}
