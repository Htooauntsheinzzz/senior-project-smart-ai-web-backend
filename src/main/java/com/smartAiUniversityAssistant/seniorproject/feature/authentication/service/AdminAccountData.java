package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import java.time.LocalDateTime;
import java.util.List;

public record AdminAccountData(Long id, String employeeId, String firstName, String lastName, String email,
        String phoneNumber, Long departmentId, String accountStatus, Long createdBy, LocalDateTime createdAt,
        Long updatedBy, LocalDateTime updatedAt, List<AdminRoleAssignmentData> roles) {
    public AdminAccountData {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
