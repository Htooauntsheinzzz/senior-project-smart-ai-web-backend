package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only the message and, for a follow-up, the conversationId returned by an earlier answer come from the client;
 * identity, model, prompt and generation settings stay backend-side. The conversationId format and ownership are
 * checked by the service (INVALID_CONVERSATION_ID / CONVERSATION_NOT_FOUND).
 */
public record StudentAssistantChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 4000, message = "Message must not exceed 4000 characters")
        String message,
        String conversationId) {

    public StudentAssistantChatRequest(String message) {
        this(message, null);
    }
}
