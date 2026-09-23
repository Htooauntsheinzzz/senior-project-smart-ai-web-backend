package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;
import java.util.List;
public record CurrentUserResponse(Long id, String employeeId, String firstName, String lastName,
        String email, String phoneNumber, Long departmentId, String accountStatus,
        boolean forcePasswordChange, List<RoleResponse> roles) {}
