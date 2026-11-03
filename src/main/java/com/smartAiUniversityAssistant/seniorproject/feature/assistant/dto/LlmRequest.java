package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.List;

/**
 * Internal, provider-neutral request; never exposed through the Student API. tools are the approved declarations
 * offered to the model; toolRounds are the earlier tool calls of this same chat request with their results.
 * requireTool asks the provider to answer this turn with a tool call rather than text (the grounding retry).
 * history is the earlier USER/ASSISTANT messages of the conversation (Step 10), oldest first; it is sent as
 * ordinary user and assistant messages before userMessage, never as system instructions.
 */
public record LlmRequest(String systemPrompt, String userMessage, String requestId,
        List<AcademicAiToolDefinition> tools, List<LlmToolRound> toolRounds, boolean requireTool,
        List<ConversationMessage> history) {
    public LlmRequest {
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolRounds = toolRounds == null ? List.of() : List.copyOf(toolRounds);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public LlmRequest(String systemPrompt, String userMessage, String requestId,
            List<AcademicAiToolDefinition> tools, List<LlmToolRound> toolRounds, boolean requireTool) {
        this(systemPrompt, userMessage, requestId, tools, toolRounds, requireTool, List.of());
    }

    public LlmRequest(String systemPrompt, String userMessage, String requestId,
            List<AcademicAiToolDefinition> tools, List<LlmToolRound> toolRounds) {
        this(systemPrompt, userMessage, requestId, tools, toolRounds, false);
    }

    public LlmRequest(String systemPrompt, String userMessage, String requestId) {
        this(systemPrompt, userMessage, requestId, List.of(), List.of(), false);
    }
}
