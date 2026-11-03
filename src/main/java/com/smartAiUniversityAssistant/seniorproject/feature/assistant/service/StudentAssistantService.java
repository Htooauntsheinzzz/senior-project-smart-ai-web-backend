package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.StudentAssistantChatRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.StudentAssistantChatResponse;

/** Student AI assistant. The Student is always the authenticated principal, never a request value. */
public interface StudentAssistantService {
    StudentAssistantChatResponse chat(StudentAssistantChatRequest request);
}
