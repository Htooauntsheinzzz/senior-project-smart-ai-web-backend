package com.smartAiUniversityAssistant.seniorproject.feature.lecture.dto;

import java.util.List;

public record LecturePageResponse(List<LectureListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
