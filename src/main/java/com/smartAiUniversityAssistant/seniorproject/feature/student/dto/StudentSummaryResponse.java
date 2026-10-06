package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

public record StudentSummaryResponse(long totalStudents, long activeStudents, long pendingStudents,
        long inactiveOrSuspendedStudents) {}
