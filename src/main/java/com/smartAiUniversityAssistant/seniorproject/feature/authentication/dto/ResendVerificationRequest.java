package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;

import jakarta.validation.constraints.*;

/** Spec file 61: asks for a new verification mail. The email is trimmed and lower-cased. */
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 255) String email) {
    public ResendVerificationRequest { if (email != null) email = email.strip().toLowerCase(java.util.Locale.ROOT); }
}
