package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

public record StudentRegisterResponse(Long id, String studentCode, String universityEmail,
        String firstName, String lastName, String accountStatus) {}
