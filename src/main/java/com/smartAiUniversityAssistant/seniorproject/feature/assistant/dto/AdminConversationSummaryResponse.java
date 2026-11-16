package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;

/** One row of the Admin Chat History list (spec file 59). updatedAt is the time of the latest completed turn. */
public record AdminConversationSummaryResponse(String conversationId, String title, String lastMessagePreview,
        int messageCount, Instant createdAt, Instant updatedAt, AssistantStudentReference student) {}
