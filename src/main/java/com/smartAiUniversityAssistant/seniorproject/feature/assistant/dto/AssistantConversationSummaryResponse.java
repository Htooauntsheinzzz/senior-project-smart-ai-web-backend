package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;

/** One row of the Student's conversation list. updatedAt is the time of the latest completed turn. */
public record AssistantConversationSummaryResponse(String conversationId, String title, String lastMessagePreview,
        int messageCount, Instant createdAt, Instant updatedAt) {}
