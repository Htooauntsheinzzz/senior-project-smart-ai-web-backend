package com.smartAiUniversityAssistant.seniorproject.feature.semester.dto;

import java.util.List;

public record SemesterPageResponse(List<SemesterListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
