package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto;

import java.util.List;

public record CurriculumPageResponse(List<CurriculumListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
