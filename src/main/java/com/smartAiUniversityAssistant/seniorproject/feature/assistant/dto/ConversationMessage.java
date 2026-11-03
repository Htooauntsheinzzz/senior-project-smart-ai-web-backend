package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;

/** One stored message of a conversation: the student's text (USER) or the final answer (ASSISTANT), nothing else. */
public record ConversationMessage(String role, String content, Instant createdAt) {
    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";

    public static ConversationMessage user(String content, Instant createdAt) {
        return new ConversationMessage(USER, content, createdAt);
    }

    public static ConversationMessage assistant(String content, Instant createdAt) {
        return new ConversationMessage(ASSISTANT, content, createdAt);
    }
}
