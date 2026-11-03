package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import java.util.*;

/**
 * Test-only decorator in front of the model (scripted, or the real ExternalLlmClient in the live run): it records
 * every provider request and response of the current case so the evaluation can observe the tool calls the model made,
 * without adding any debug field to the Student response. maxCalls is a hard cap on provider calls for the whole
 * run; past it the call fails as a provider failure instead of reaching the provider.
 */
public final class RecordingLlmClient implements LlmClient {
    public static final String BUDGET_EXHAUSTED = "EVAL_PROVIDER_CALL_BUDGET_EXHAUSTED";
    private final List<LlmRequest> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<LlmResponse> responses = Collections.synchronizedList(new ArrayList<>());
    private volatile LlmClient delegate;
    private volatile int maxCalls = Integer.MAX_VALUE;
    private int totalCalls;

    public void delegateTo(LlmClient delegate) { this.delegate = delegate; }
    public void maxCalls(int maxCalls) { this.maxCalls = maxCalls; }
    public synchronized int totalCalls() { return totalCalls; }

    /** Forget the previous case's requests (the run's call total is kept). */
    public void startCase() {
        requests.clear();
        responses.clear();
    }

    public List<LlmRequest> requests() { return List.copyOf(requests); }

    /** The model's answers to this case's requests, in order (a failed provider call has none). */
    public List<LlmResponse> responses() { return List.copyOf(responses); }

    @Override
    public LlmResponse chat(LlmRequest request) {
        synchronized (this) {
            if (totalCalls >= maxCalls) throw new AiAssistantFailure(503, BUDGET_EXHAUSTED, "Evaluation provider-call budget reached.");
            totalCalls++;
        }
        requests.add(request);
        LlmResponse response = Objects.requireNonNull(delegate, "no model configured").chat(request);
        responses.add(response);
        return response;
    }
}
