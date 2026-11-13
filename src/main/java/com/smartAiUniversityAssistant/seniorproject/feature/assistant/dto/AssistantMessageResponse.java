package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;
import java.util.List;

/** One stored message. sources is never null: [] for a USER message and for an answer that used no document. */
public record AssistantMessageResponse(long messageId, String role, String content, Instant createdAt,
        List<StudentAssistantChatResponse.Source> sources) {}
