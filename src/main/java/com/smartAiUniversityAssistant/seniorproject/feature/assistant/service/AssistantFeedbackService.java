package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

/**
 * Spec file 59: feedback on assistant answers. A Student rates an answer of their own conversation; Admins list,
 * review and export the ratings. from and to are ISO-8601 instants (from inclusive, to exclusive) on the submission time.
 */
public interface AssistantFeedbackService {
    /** The signed-in Student rates (or rates again) one answer of their own conversation. */
    AssistantFeedbackResponse submitMyFeedback(String conversationId, AssistantFeedbackRequest request);

    AdminFeedbackPageResponse list(AuthenticatedUser actor, int page, int size, String search, String rating, String reviewStatus,
            String from, String to);

    AdminFeedbackStatisticsResponse statistics(AuthenticatedUser actor);

    AdminFeedbackResponse detail(AuthenticatedUser actor, long id);

    AdminFeedbackResponse review(AuthenticatedUser actor, long id, AdminFeedbackReviewRequest request);

    /** The filtered list as CSV, newest first, at most MAX_EXPORT_ROWS rows. */
    byte[] exportCsv(AuthenticatedUser actor, String search, String rating, String reviewStatus, String from, String to);
}
