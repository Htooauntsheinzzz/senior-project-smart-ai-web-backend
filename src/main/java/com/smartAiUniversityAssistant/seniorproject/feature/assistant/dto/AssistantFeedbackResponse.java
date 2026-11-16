package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;

/** The Student's stored rating of one answer. */
public record AssistantFeedbackResponse(long messageId, String rating, String comment, Instant submittedAt) {}
