package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;
import java.util.List;

/**
 * A stored conversation with one window of its messages, oldest first, for the Admin transcript (spec file 59).
 * hasMoreBefore: older messages exist; ask again with beforeMessageId set to the first messageId of this window.
 */
public record AdminConversationDetailResponse(String conversationId, String title, int messageCount, Instant createdAt,
        Instant updatedAt, AssistantStudentReference student, boolean hasMoreBefore, List<Message> messages) {

    /** model and feedback are null for a USER message; feedback is null for an answer the Student did not rate. */
    public record Message(long messageId, String role, String content, Instant createdAt, String model,
            List<StudentAssistantChatResponse.Source> sources, Feedback feedback) {}

    public record Feedback(long id, String rating, String comment, String reviewStatus) {}
}
