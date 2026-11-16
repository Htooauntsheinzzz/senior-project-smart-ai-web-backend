package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminAssistantAnswerResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminAssistantChatRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminKnowledgeTestRequest;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

/**
 * Spec file 59: the assistant for Admins. Both methods search the Ready knowledge documents with the Admin search of
 * spec file 52 and let the model answer from the passages only. Nothing of an Admin request is stored.
 */
public interface AdminAssistantService {
    /** AI Assistant screen: one turn of the Admin's chat. Without a matching passage the model still answers (greetings, or "not found"). */
    AdminAssistantAnswerResponse chat(AuthenticatedUser actor, AdminAssistantChatRequest request);

    /** Test AI Knowledge screen: one question. Without a matching passage the fixed NO_PASSAGE_MESSAGE is returned and no model is called. */
    AdminAssistantAnswerResponse testKnowledge(AuthenticatedUser actor, AdminKnowledgeTestRequest request);
}
