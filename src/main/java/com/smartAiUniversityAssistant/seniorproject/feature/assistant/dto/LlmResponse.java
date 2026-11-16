package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.List;

/**
 * Internal, provider-neutral turn result: either final text or one or more tool calls. providerRequestId is for
 * server logs only. providerMessage is the provider's own assistant message, kept opaque and sent back unchanged
 * with the tool results (Gemini rejects a tool round whose thought signature is missing). usage is null when the
 * provider reported no token counts.
 */
public record LlmResponse(String content, String model, String providerRequestId, List<LlmToolCall> toolCalls,
        Object providerMessage, LlmUsage usage) {
    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public LlmResponse(String content, String model, String providerRequestId, List<LlmToolCall> toolCalls,
            Object providerMessage) {
        this(content, model, providerRequestId, toolCalls, providerMessage, null);
    }

    public LlmResponse(String content, String model, String providerRequestId) {
        this(content, model, providerRequestId, List.of(), null, null);
    }

    public boolean hasToolCalls() { return !toolCalls.isEmpty(); }
}
