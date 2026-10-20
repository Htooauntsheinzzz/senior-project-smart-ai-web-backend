package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

public record EnrollmentSummaryResponse(long totalEnrollments, long activeEnrollments, long pendingEnrollments,
        long withdrawnEnrollments, long droppedEnrollments, long withdrawnOrDropped) {}
