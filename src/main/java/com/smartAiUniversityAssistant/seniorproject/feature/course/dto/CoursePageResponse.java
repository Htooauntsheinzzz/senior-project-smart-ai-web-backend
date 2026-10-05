package com.smartAiUniversityAssistant.seniorproject.feature.course.dto;

import java.util.List;

public record CoursePageResponse(List<CourseListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
