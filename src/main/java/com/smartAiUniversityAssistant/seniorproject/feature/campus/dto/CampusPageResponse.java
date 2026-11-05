package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import java.util.List;

public record CampusPageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
        boolean first, boolean last, List<String> sort) {}
