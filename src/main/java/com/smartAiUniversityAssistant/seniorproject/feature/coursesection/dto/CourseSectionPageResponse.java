package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import java.util.List;

public record CourseSectionPageResponse(List<CourseSectionListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
