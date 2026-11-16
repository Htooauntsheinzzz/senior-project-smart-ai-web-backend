package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;
import java.util.List;

/**
 * A conversation with one window of its messages, oldest first. hasMoreBefore: older messages exist; ask again with
 * beforeMessageId set to the first messageId of this window.
 */
public record AssistantConversationDetailResponse(String conversationId, String title, int messageCount, Instant createdAt,
        Instant updatedAt, boolean hasMoreBefore, List<AssistantMessageResponse> messages) {}
