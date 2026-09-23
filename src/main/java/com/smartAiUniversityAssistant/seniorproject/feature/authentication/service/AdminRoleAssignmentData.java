package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import java.time.LocalDateTime;

public record AdminRoleAssignmentData(Long roleId, String roleCode, String roleName, boolean active,
        Long assignedBy, LocalDateTime assignedAt) {}
