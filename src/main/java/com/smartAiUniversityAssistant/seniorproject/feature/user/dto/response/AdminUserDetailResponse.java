package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response;

import java.time.Instant;
import java.util.List;

public record AdminUserDetailResponse(Long id, String employeeId, String firstName, String lastName,
        String email, String phoneNumber, Long departmentId, String accountStatus,
        List<AdminUserRoleResponse> roles, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
