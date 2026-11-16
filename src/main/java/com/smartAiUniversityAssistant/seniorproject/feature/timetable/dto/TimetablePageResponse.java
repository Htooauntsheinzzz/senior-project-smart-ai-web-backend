package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import java.util.List;

public record TimetablePageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
        boolean first, boolean last, List<String> sort) {}
