package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.List;

/** One completed tool round: the model's tool-call turn and the backend's result for each call. */
public record LlmToolRound(LlmResponse assistantTurn, List<LlmToolResult> results) {
    public LlmToolRound {
        results = List.copyOf(results);
    }
}
