package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Step 17 (spec file 58): the persistent side of a conversation. PostgreSQL keeps every completed turn until the Student
 * deletes the conversation; Redis (Step 10) stays the short-term context that is sent to the model. The Student of the
 * three API methods is always the authenticated principal, never a request value; another Student's conversation is
 * reported exactly like a missing one (CONVERSATION_NOT_FOUND).
 */
public interface AssistantConversationHistoryService {
    /** False when AI_HISTORY_ENABLED=false: nothing is stored or restored. */
    boolean enabled();

    /**
     * Stores one completed turn. Inside one transaction: the conversation row is inserted (first stored turn) or locked,
     * the two messages are inserted, then shortTermSave runs (the Redis save of Step 10) and the row takes the saved
     * state's version, reference and time. A failure of shortTermSave rolls the turn back and is rethrown unchanged.
     */
    AssistantConversationContext recordTurn(AssistantConversationContext previous, ConversationMessage userMessage,
            ConversationMessage assistantMessage, AssistantTurnDetails details,
            Supplier<AssistantConversationContext> shortTermSave);

    /**
     * The context of a stored conversation whose Redis value expired: its newest maxTurns turns, the stored academic
     * reference and version. Empty when history is off, the conversation does not exist or another Student owns it.
     */
    Optional<AssistantConversationContext> restoreContext(String conversationId, long studentId, int maxTurns);

    AssistantConversationPageResponse listMyConversations(int page, int size);

    AssistantConversationDetailResponse getMyConversation(String conversationId, int limit, Long beforeMessageId);

    /** Removes the Redis context and the stored conversation with its messages. */
    void deleteMyConversation(String conversationId);
}
