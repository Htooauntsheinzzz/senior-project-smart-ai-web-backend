package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AdminUserRoleResponse(Long id, String roleCode, String roleName,
        @JsonProperty("isActive") Boolean isActive, Long assignedBy, Instant assignedAt) {}
