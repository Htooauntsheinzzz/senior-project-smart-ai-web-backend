package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;

import jakarta.validation.constraints.*;

/** Spec file 61: the token of the verification link. */
public record VerifyEmailRequest(@NotBlank @Size(max = 128) String token) {
    public VerifyEmailRequest { if (token != null) token = token.strip(); }
    @Override public String toString() { return "VerifyEmailRequest[REDACTED]"; }
}
