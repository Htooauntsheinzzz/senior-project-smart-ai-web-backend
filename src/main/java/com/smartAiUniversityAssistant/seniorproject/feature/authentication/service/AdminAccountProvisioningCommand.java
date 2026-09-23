package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

public record AdminAccountProvisioningCommand(String employeeId, String phoneNumber,
        String firstName, String lastName, String email, Long departmentId,
        String accountStatus, Long roleId, String temporaryPassword,
        boolean forcePasswordChange) {
    @Override public String toString() { return "AdminAccountProvisioningCommand[REDACTED]"; }
}
