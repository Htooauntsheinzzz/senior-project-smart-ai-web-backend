package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;

/**
 * What actually happened for one chat request, observed in test mode only (never added to the Student response):
 * the HTTP outcome, the final message, and every tool call the model made with its arguments and the backend's
 * outcome. toolResultsJson is the serialized tool results the model received, the evidence an answer may rest on.
 * dataModified: the enrollment fingerprint changed during the request.
 */
public record EvaluationTrace(String requestId, int httpStatus, String errorCode, String message, String model,
        List<ObservedToolCall> toolCalls, String toolResultsJson, int providerRequests, long durationMs,
        boolean dataModified) {

    public EvaluationTrace {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResultsJson = toolResultsJson == null ? "[]" : toolResultsJson;
    }

    /** round counts from 1; code is the tool's error code when success is false. */
    public record ObservedToolCall(int round, String name, Map<String, Object> arguments, boolean success, String code) {}

    /** Distinct tool names in call order. */
    public List<String> toolNames() {
        return toolCalls.stream().map(ObservedToolCall::name).distinct().toList();
    }
}
