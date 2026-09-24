package com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto;

public record FacultySummaryResponse(long totalFaculties, long activeFaculties,
        long inactiveFaculties, long totalDepartments) {}
