package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.util.List;

public record DocumentCategoryPageResponse(List<DocumentCategoryResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
