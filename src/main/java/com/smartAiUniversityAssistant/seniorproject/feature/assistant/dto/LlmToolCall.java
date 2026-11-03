package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.Map;

/** A tool the model asked for. arguments is null when the model's argument JSON was not a JSON object. */
public record LlmToolCall(String callId, String name, Map<String, Object> arguments) {}
