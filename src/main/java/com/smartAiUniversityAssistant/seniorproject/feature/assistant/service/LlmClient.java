package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;

/** Provider-neutral boundary to the cloud LLM. Implementations throw only {@link AiAssistantFailure}. */
public interface LlmClient {
    LlmResponse chat(LlmRequest request);
}
