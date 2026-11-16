package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminConversationDetailResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AdminConversationPageResponse;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;

/**
 * Spec file 59: the Admin's read-only view of the stored Student conversations (spec file 58). Admins never change or
 * delete a conversation. from and to are ISO-8601 instants (from inclusive, to exclusive) on the latest activity.
 */
public interface AdminAssistantHistoryService {
    AdminConversationPageResponse list(AuthenticatedUser actor, int page, int size, String search, String from, String to);

    AdminConversationDetailResponse detail(AuthenticatedUser actor, String conversationId, int limit, Long beforeMessageId);

    /** The filtered list as CSV, newest first, at most MAX_EXPORT_ROWS rows. */
    byte[] exportCsv(AuthenticatedUser actor, String search, String from, String to);
}
