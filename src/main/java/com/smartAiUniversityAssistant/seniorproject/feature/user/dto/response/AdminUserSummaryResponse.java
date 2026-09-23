package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminUserSummaryResponse(Long id, String employeeId, String firstName, String lastName,
        String email, String phoneNumber, Long departmentId, String accountStatus,
        List<AdminUserRoleResponse> roles, Instant createdAt, Instant updatedAt) {}
