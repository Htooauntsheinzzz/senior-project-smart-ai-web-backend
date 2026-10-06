package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import java.util.List;

public record StudentPageResponse(List<StudentListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
