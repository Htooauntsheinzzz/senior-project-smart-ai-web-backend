package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import java.util.List;

/**
 * Step 10 follow-up references. Gemini still interprets the question and chooses the tools; this component only
 * validates what "it" or "the first one" can point to in this Student's conversation, tells the model that (or that
 * it must ask), and records which courses an answer showed. It never answers an academic question itself.
 */
public interface AcademicReferenceResolver {

    /** How the current message refers back to the conversation; NONE for a new conversation. */
    ResolvedAcademicReference resolve(String currentMessage, AssistantConversationContext context);

    /**
     * The backend-written conversation section appended to the system prompt for a continued conversation (empty for
     * a new one). It holds course codes and section ids taken from earlier tool results, never student text.
     */
    String promptContext(ResolvedAcademicReference resolved, AssistantConversationContext context);

    /** Whether the answer must rest on a fresh tool result: a clear follow-up question about a remembered course. */
    boolean requiresAcademicEvidence(ResolvedAcademicReference resolved, String currentMessage);

    /** The reference after a completed turn, derived from this turn's successful tool results and the final answer. */
    AcademicConversationReference nextReference(AcademicConversationReference previous, List<LlmToolRound> rounds,
            String finalAnswer);
}
