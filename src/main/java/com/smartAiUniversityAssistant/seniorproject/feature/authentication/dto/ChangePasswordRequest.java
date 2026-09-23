package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;
import jakarta.validation.constraints.*;
public record ChangePasswordRequest(@NotEmpty @Size(max=72) String currentPassword,
                                    @NotNull String newPassword) {
    @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
}
