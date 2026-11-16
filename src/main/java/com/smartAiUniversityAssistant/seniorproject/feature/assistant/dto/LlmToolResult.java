package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

/** What goes back to the model for one tool call: only safe, minimal structured data or a safe error. */
public record LlmToolResult(String callId, String toolName, Object result) {}
