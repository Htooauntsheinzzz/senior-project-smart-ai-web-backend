package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request;

import jakarta.validation.constraints.*;

public record UpdateAdminUserStatusRequest(
        @NotNull @Pattern(regexp = "ACTIVE|INACTIVE|LOCKED|SUSPENDED") String accountStatus) {
    @Override public String toString() { return "UpdateAdminUserStatusRequest[REDACTED]"; }
}
