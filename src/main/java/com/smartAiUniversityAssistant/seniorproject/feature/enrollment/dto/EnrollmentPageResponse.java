package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import java.util.List;

public record EnrollmentPageResponse(List<EnrollmentListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
