package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;

/** conversationId is null only when conversation context is disabled (AI_CONTEXT_ENABLED=false). */
public record StudentAssistantChatResponse(String message, String requestId, String conversationId, String model,
        Instant createdAt) {}
