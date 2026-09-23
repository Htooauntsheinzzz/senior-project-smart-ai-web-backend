package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;
import jakarta.validation.constraints.*;
public record RefreshTokenRequest(@NotBlank @Size(max=512) String refreshToken) {
    @Override public String toString() { return "RefreshTokenRequest[REDACTED]"; }
}
