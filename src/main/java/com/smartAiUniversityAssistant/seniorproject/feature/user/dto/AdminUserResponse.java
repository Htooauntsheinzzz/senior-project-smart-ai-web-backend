package com.smartAiUniversityAssistant.seniorproject.feature.user.dto;

import java.time.Instant;
import java.util.List;

public record AdminUserResponse(Long id, String employeeId, String phoneNumber,
        String firstName, String lastName, String email, Long departmentId,
        String accountStatus, List<AdminUserRoleResponse> roles,
        boolean forcePasswordChange, Long createdBy, Instant createdAt) {}
