package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto;

import java.util.List;

public record CourseResultPageResponse(List<CourseResultResponse> content, int page, int size, long totalElements,
        int totalPages, boolean first, boolean last) {}
