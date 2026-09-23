package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import java.time.Instant;

public record ProvisionedAdminAccount(Long id, String employeeId, String phoneNumber,
        String firstName, String lastName, String email, Long departmentId,
        String accountStatus, Long roleId, String roleCode, String roleName,
        boolean forcePasswordChange, Long createdBy, Instant createdAt) {}
