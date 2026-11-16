package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

/** The four cards of the AI Feedback screen, over all feedback (not the filtered list). */
public record AdminFeedbackStatisticsResponse(long totalFeedback, long positive, long negative, long awaitingReview) {}
