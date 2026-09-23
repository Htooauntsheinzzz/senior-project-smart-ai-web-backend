package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

public record UpdateAdminAccountCommand(String employeeId, String phoneNumber, String firstName,
        String lastName, String email, Long departmentId, String accountStatus, Long roleId) {}
