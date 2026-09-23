package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotBlank @Email @Size(max=255) String email,
                           @NotEmpty @Size(max=72) String password) {
    public LoginRequest { if (email != null) email = email.strip(); }
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
