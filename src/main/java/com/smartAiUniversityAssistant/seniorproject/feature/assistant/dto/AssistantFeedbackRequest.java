package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import jakarta.validation.constraints.*;

/**
 * Body of PUT /api/v1/student/assistant/conversations/{conversationId}/feedback (spec file 59). The answer is named by
 * exactly one of messageId (from the conversation detail) or requestId (from the chat response).
 */
public record AssistantFeedbackRequest(@Positive Long messageId, @Size(max = 36) String requestId,
        @NotNull @Pattern(regexp = "POSITIVE|NEGATIVE") String rating, @Size(max = 1000) String comment) {}
