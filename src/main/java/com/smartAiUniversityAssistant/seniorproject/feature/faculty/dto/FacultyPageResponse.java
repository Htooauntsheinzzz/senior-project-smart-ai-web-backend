package com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto;

import java.util.List;

public record FacultyPageResponse(List<FacultyListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
