package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mode A stand-in for Gemini: plays a case's script by provider call. {"tools": [...]} requests those calls,
 * {"text": "..."} answers, {"providerError": 429} fails like the provider. Past the end the last step repeats.
 * ${FROM_PREVIOUS} is the courseSectionId of the previous round's first result. It proves the backend pipeline only;
 * it says nothing about whether Gemini would choose these tools.
 */
final class ScriptedLlmClient implements LlmClient {
    private final List<Map<String, Object>> steps;
    private final AtomicInteger calls = new AtomicInteger();

    ScriptedLlmClient(List<Map<String, Object>> steps) {
        if (steps.isEmpty()) throw new IllegalArgumentException("empty script");
        this.steps = List.copyOf(steps);
    }

    @Override
    @SuppressWarnings("unchecked")
    public LlmResponse chat(LlmRequest request) {
        int index = Math.min(calls.getAndIncrement(), steps.size() - 1);
        Map<String, Object> step = steps.get(index);
        if (step.get("providerError") instanceof Integer status)
            throw status == 429 ? AiAssistantFailure.rateLimited() : AiAssistantFailure.unavailable();
        if (step.containsKey("text")) return new LlmResponse((String) step.get("text"), "scripted-model", null);
        var toolCalls = new ArrayList<LlmToolCall>();
        for (var call : (List<Map<String, Object>>) step.get("tools")) {
            var args = new LinkedHashMap<String, Object>();
            ((Map<String, Object>) call.get("arguments")).forEach((k, v) -> args.put(k, "${FROM_PREVIOUS}".equals(v) ? previousSection(request) : v));
            toolCalls.add(new LlmToolCall("call-" + index + "-" + toolCalls.size(), (String) call.get("name"), args));
        }
        return new LlmResponse(null, "scripted-model", null, toolCalls, null);
    }

    @SuppressWarnings("unchecked")
    private static Object previousSection(LlmRequest request) {
        var previous = (Map<String, Object>) request.toolRounds().getLast().results().getFirst().result();
        return ((Map<String, Object>) previous.get("data")).get("courseSectionId");
    }
}
