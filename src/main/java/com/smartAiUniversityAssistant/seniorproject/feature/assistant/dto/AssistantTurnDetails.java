package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.List;

/**
 * Step 17: what the history keeps about one completed turn besides the two messages: the chat requestId, the model of
 * the answer and the sources the Student was shown. Never stored in Redis.
 */
public record AssistantTurnDetails(String requestId, String model, List<StudentAssistantChatResponse.Source> sources) {
    public static final AssistantTurnDetails NONE = new AssistantTurnDetails(null, null, List.of());

    public AssistantTurnDetails {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
