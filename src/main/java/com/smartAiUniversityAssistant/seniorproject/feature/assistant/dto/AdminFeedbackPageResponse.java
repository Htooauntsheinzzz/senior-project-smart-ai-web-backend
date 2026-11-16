package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.List;

public record AdminFeedbackPageResponse(List<AdminFeedbackResponse> content, int page, int size, long totalElements,
        int totalPages, boolean first, boolean last) {}
