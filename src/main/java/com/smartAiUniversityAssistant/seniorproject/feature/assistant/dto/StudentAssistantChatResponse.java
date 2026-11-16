package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;
import java.util.List;

/**
 * conversationId is null only when conversation context is disabled (AI_CONTEXT_ENABLED=false). Step 15A: sources lists
 * the documents of the passages this answer used (title, academic year, category), never null, [] when none was used.
 */
public record StudentAssistantChatResponse(String message, String requestId, String conversationId, String model,
        Instant createdAt, List<Source> sources) {

    public StudentAssistantChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    /** Existing call sites and tests: no sources. */
    public StudentAssistantChatResponse(String message, String requestId, String conversationId, String model, Instant createdAt) {
        this(message, requestId, conversationId, model, createdAt, List.of());
    }

    /** One knowledge document used for the answer. No id, file name or link: Students cannot download documents. */
    public record Source(String title, String academicYear, String category) {}
}
