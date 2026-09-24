package com.smartAiUniversityAssistant.seniorproject.feature.department.dto;

import java.util.List;

public record DepartmentPageResponse(List<DepartmentListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
