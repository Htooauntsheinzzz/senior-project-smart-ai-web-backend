package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import jakarta.validation.constraints.*;

public record StudentLoginRequest(@NotBlank @Email @Size(max = 255) String universityEmail, @NotBlank String password) {
    public StudentLoginRequest {
        universityEmail = universityEmail == null ? null : universityEmail.trim().toLowerCase(java.util.Locale.ROOT);
    }
    @Override public String toString() { return "StudentLoginRequest[REDACTED]"; }
}
