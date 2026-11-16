package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;
import java.util.List;

/**
 * One feedback record for the Admin AI Feedback screen (spec file 59). In the list question and answer are shortened
 * to 300 characters, model is null and sources is empty; the detail has the full texts, the model and the sources.
 * question is the Student's message the rated answer replied to, null when it is no longer stored.
 */
public record AdminFeedbackResponse(long id, String rating, String comment, String reviewStatus, String reviewNote,
        Reviewer reviewedBy, Instant reviewedAt, Instant submittedAt, AssistantStudentReference student, String conversationId,
        String conversationTitle, long messageId, String question, String answer, String model,
        List<StudentAssistantChatResponse.Source> sources) {

    public record Reviewer(long id, String displayName) {}
}
