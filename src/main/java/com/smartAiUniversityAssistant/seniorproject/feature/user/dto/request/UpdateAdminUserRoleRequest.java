package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request;

import jakarta.validation.constraints.*;

public record UpdateAdminUserRoleRequest(@NotNull @Positive Long roleId) {
    @Override public String toString() { return "UpdateAdminUserRoleRequest[REDACTED]"; }
}
