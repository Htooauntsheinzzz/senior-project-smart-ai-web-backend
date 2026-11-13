package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.List;

public record AssistantConversationPageResponse(List<AssistantConversationSummaryResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {}
