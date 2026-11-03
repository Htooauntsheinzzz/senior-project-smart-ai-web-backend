package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicConversationReference;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AssistantConversationContext;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.ConversationMessage;

/**
 * Step 10 short-lived conversation state. The owner is always the authenticated Student passed in by the caller,
 * never a request value. Failures are {@link com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure}s:
 * INVALID_CONVERSATION_ID (400), CONVERSATION_NOT_FOUND (404, also for another student's conversation),
 * CONVERSATION_CONFLICT (409) and ASSISTANT_CONTEXT_UNAVAILABLE (503).
 */
public interface ConversationContextService {
    /** False when AI_CONTEXT_ENABLED=false: every chat is then a single, unsaved question. */
    boolean enabled();

    /** A new conversation with a fresh random id; nothing is stored until its first turn completes. */
    AssistantConversationContext createNew(long studentId);

    /** Loads a conversation the Student owns. Reading never extends its TTL. */
    AssistantConversationContext getOwnedConversation(String conversationId, long studentId);

    /**
     * Appends one completed USER/ASSISTANT pair, replaces the academic reference, trims to the configured limits,
     * increments the version and refreshes the TTL, atomically against concurrent turns. Returns the saved state.
     */
    AssistantConversationContext saveCompletedTurn(AssistantConversationContext context, ConversationMessage userMessage,
            ConversationMessage assistantMessage, AcademicConversationReference reference);
}
