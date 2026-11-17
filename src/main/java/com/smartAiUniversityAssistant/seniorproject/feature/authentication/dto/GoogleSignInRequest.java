package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;

import jakarta.validation.constraints.*;

/** Spec file 61: the ID token ("credential") the Google sign-in button gave the admin web. */
public record GoogleSignInRequest(@NotBlank @Size(max = 4096) String idToken) {
    public GoogleSignInRequest { if (idToken != null) idToken = idToken.strip(); }
    @Override public String toString() { return "GoogleSignInRequest[REDACTED]"; }
}
