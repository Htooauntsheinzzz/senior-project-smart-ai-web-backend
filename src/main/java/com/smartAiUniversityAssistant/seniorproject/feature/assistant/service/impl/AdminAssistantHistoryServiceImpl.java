package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessage;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessageFeedback;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AssistantConversationMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AdminAssistantQueryRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AdminAssistantQueryRepository.ConversationFilter;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AdminAssistantQueryRepository.ConversationRow;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantMessageFeedbackRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantMessageRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AdminAssistantHistoryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantCsv;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec file 59: Admins read the stored Student conversations; nothing is changed here. Reading a transcript is logged
 * with the Admin's id and the conversation id (never a title or message text), so that access to Student chats can be
 * traced.
 */
@Service
public class AdminAssistantHistoryServiceImpl implements AdminAssistantHistoryService {
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_MESSAGE_LIMIT = 100;
    static final int MAX_SEARCH_CHARS = 100;
    public static final int MAX_EXPORT_ROWS = 5000;
    private static final Logger log = LoggerFactory.getLogger(AdminAssistantHistoryServiceImpl.class);

    private final AdminAssistantQueryRepository queries;
    private final AssistantMessageRepository messages;
    private final AssistantMessageFeedbackRepository feedback;
    private final AssistantConversationMapper mapper;

    public AdminAssistantHistoryServiceImpl(AdminAssistantQueryRepository queries, AssistantMessageRepository messages,
            AssistantMessageFeedbackRepository feedback, AssistantConversationMapper mapper) {
        this.queries = queries;
        this.messages = messages;
        this.feedback = feedback;
        this.mapper = mapper;
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true)
    public AdminConversationPageResponse list(AuthenticatedUser actor, int page, int size, String search, String from, String to) {
        requirePage(page, size, MAX_PAGE_SIZE);
        var filter = new ConversationFilter(search(search), instant(from), instant(to));
        requireRange(filter.from(), filter.to());
        long total = queries.countConversations(filter);
        var rows = total == 0 ? List.<ConversationRow>of() : queries.findConversations(filter, size, (long) page * size);
        int totalPages = (int) Math.ceil((double) total / size);
        return new AdminConversationPageResponse(rows.stream().map(AdminAssistantHistoryServiceImpl::summary).toList(), page, size,
                total, totalPages, page == 0, page >= totalPages - 1);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true)
    public AdminConversationDetailResponse detail(AuthenticatedUser actor, String conversationId, int limit, Long beforeMessageId) {
        UUID id = UUID.fromString(ConversationContextServiceImpl.normalizeId(conversationId));
        if (limit < 1 || limit > MAX_MESSAGE_LIMIT || beforeMessageId != null && beforeMessageId < 1)
            throw AiAssistantFailure.invalidMessage();
        ConversationRow conversation = queries.findConversation(id).orElseThrow(AiAssistantFailure::conversationNotFound);
        // One more than asked for tells whether older messages exist.
        var limitPlusOne = PageRequest.of(0, limit + 1);
        var newest = new ArrayList<>(beforeMessageId == null ? messages.findNewest(id, limitPlusOne)
                : messages.findNewestBefore(id, beforeMessageId, limitPlusOne));
        boolean hasMoreBefore = newest.size() > limit;
        if (hasMoreBefore) newest.removeLast();
        Collections.reverse(newest);
        Map<Long, AssistantMessageFeedback> rated = newest.isEmpty() ? Map.of()
                : feedback.findByMessageIdIn(newest.stream().map(AssistantMessage::getId).toList()).stream()
                        .collect(Collectors.toMap(AssistantMessageFeedback::getMessageId, f -> f));
        log.info("ADMIN_ASSISTANT_TRANSCRIPT_READ actorUserId={} conversationId={} messages={}", actor.userId(), id, newest.size());
        return new AdminConversationDetailResponse(id.toString(), conversation.title(), conversation.messageCount(),
                conversation.createdAt(), conversation.updatedAt(), student(conversation), hasMoreBefore,
                newest.stream().map(m -> message(m, rated.get(m.getId()))).toList());
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true)
    public byte[] exportCsv(AuthenticatedUser actor, String search, String from, String to) {
        var filter = new ConversationFilter(search(search), instant(from), instant(to));
        requireRange(filter.from(), filter.to());
        var rows = queries.findConversations(filter, MAX_EXPORT_ROWS, 0);
        var lines = new ArrayList<List<String>>(rows.size());
        int number = 1;
        for (ConversationRow r : rows)
            lines.add(Arrays.asList(String.valueOf(number++), r.title(), r.studentCode(), r.studentName(),
                    String.valueOf(r.messageCount()), String.valueOf(r.createdAt()), String.valueOf(r.updatedAt()),
                    r.lastMessagePreview(), r.id().toString()));
        log.info("ADMIN_ASSISTANT_HISTORY_EXPORT actorUserId={} rows={}", actor.userId(), rows.size());
        return AssistantCsv.write(List.of("No.", "Conversation", "Student Code", "Student Name", "Messages", "Started At (UTC)",
                "Last Activity (UTC)", "Latest Answer Preview", "Conversation ID"), lines);
    }

    private AdminConversationDetailResponse.Message message(AssistantMessage m, AssistantMessageFeedback rating) {
        return new AdminConversationDetailResponse.Message(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt(), m.getModel(),
                mapper.sources(m.getSources()), rating == null ? null : new AdminConversationDetailResponse.Feedback(rating.getId(),
                        rating.getRating(), rating.getComment(), rating.getReviewStatus()));
    }

    private static AdminConversationSummaryResponse summary(ConversationRow r) {
        return new AdminConversationSummaryResponse(r.id().toString(), r.title(), r.lastMessagePreview(), r.messageCount(),
                r.createdAt(), r.updatedAt(), student(r));
    }

    private static AssistantStudentReference student(ConversationRow r) {
        return new AssistantStudentReference(r.studentId(), r.studentCode(), r.studentName());
    }

    // ---- shared with the feedback service --------------------------------------------------------------------------

    static void requirePage(int page, int size, int maxSize) {
        if (page < 0 || size < 1 || size > maxSize || (long) page * size > Integer.MAX_VALUE) throw AiAssistantFailure.invalidMessage();
    }

    /** Null for a missing or blank search; otherwise the trimmed text, at most MAX_SEARCH_CHARS characters. */
    static String search(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.strip();
        if (value.length() > MAX_SEARCH_CHARS) throw AiAssistantFailure.invalidMessage();
        return value;
    }

    /** Null for a missing or blank value; otherwise an ISO-8601 instant such as 2026-11-13T00:00:00Z. */
    static Instant instant(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Instant.parse(raw.strip());
        } catch (DateTimeParseException e) {
            throw AiAssistantFailure.invalidMessage();
        }
    }

    static void requireRange(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) throw AiAssistantFailure.invalidMessage();
    }
}
