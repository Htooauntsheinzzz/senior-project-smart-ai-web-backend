package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantConversation;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessage;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessageFeedback;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AssistantConversationMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AdminAssistantQueryRepository.FeedbackFilter;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AdminAssistantQueryRepository.FeedbackRow;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantCsv;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantFeedbackService;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec file 59: feedback on assistant answers. The Student of submitMyFeedback is always the authenticated principal;
 * an answer of another Student's conversation is reported like a missing conversation. Only ids, the rating and the
 * review status are logged, never a comment, a note or message text.
 */
@Service
public class AssistantFeedbackServiceImpl implements AssistantFeedbackService {
    static final int MAX_PAGE_SIZE = 50;
    static final int PREVIEW_CHARS = 300;
    public static final int MAX_EXPORT_ROWS = 5000;
    private static final Set<String> RATINGS = Set.of(AssistantMessageFeedback.POSITIVE, AssistantMessageFeedback.NEGATIVE);
    private static final Set<String> REVIEW_STATUSES = Set.of(AssistantMessageFeedback.PENDING, AssistantMessageFeedback.REVIEWED,
            AssistantMessageFeedback.RESOLVED);
    private static final Logger log = LoggerFactory.getLogger(AssistantFeedbackServiceImpl.class);

    private final AssistantMessageFeedbackRepository feedback;
    private final AssistantConversationHistoryRepository conversations;
    private final AssistantMessageRepository messages;
    private final AdminAssistantQueryRepository queries;
    private final AssistantConversationMapper mapper;
    private final CurrentStudentProvider currentStudent;
    private final StudentAcademicService academics;
    private final Clock clock;

    public AssistantFeedbackServiceImpl(AssistantMessageFeedbackRepository feedback, AssistantConversationHistoryRepository conversations,
            AssistantMessageRepository messages, AdminAssistantQueryRepository queries, AssistantConversationMapper mapper,
            CurrentStudentProvider currentStudent, StudentAcademicService academics, Clock clock) {
        this.feedback = feedback;
        this.conversations = conversations;
        this.messages = messages;
        this.queries = queries;
        this.mapper = mapper;
        this.currentStudent = currentStudent;
        this.academics = academics;
        this.clock = clock;
    }

    // ---- Student ---------------------------------------------------------------------------------------------------

    @Override
    @Transactional
    public AssistantFeedbackResponse submitMyFeedback(String conversationId, AssistantFeedbackRequest request) {
        // 401/403/PASSWORD_CHANGE_REQUIRED from the token, then the same eligibility check as the chat endpoint.
        long studentId = currentStudent.getCurrentStudentId();
        academics.requireEligibleStudent(studentId);
        UUID id = UUID.fromString(ConversationContextServiceImpl.normalizeId(conversationId));
        String requestId = request == null || request.requestId() == null ? null : request.requestId().strip();
        if (request == null || !RATINGS.contains(request.rating()) || (request.messageId() == null) == (requestId == null)
                || requestId != null && requestId.isEmpty() || request.comment() != null && request.comment().length() > 1000)
            throw AiAssistantFailure.invalidMessage();
        // The row lock serializes two ratings of the same answer and a rating with the deletion of the conversation.
        AssistantConversation conversation = conversations.findByIdForUpdate(id)
                .filter(c -> c.getStudentId().equals(studentId)).orElseThrow(AiAssistantFailure::conversationNotFound);
        AssistantMessage answer = (request.messageId() != null ? messages.findById(request.messageId())
                : messages.findAnswersByRequestId(id, requestId, PageRequest.of(0, 1)).stream().findFirst())
                .filter(m -> m.getConversationId().equals(conversation.getId()) && ConversationMessage.ASSISTANT.equals(m.getRole()))
                .orElseThrow(AiAssistantFailure::messageNotFound);

        Instant now = clock.instant();
        AssistantMessageFeedback row = feedback.findByMessageId(answer.getId()).orElse(null);
        boolean created = row == null;
        if (created) {
            row = new AssistantMessageFeedback();
            row.setMessageId(answer.getId());
            row.setConversationId(id);
            row.setStudentId(studentId);
            row.setCreatedAt(now);
        }
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();
        row.setRating(request.rating());
        row.setComment(comment);
        row.setSubmittedAt(now);
        // A new or changed rating has not been looked at yet.
        row.setReviewStatus(AssistantMessageFeedback.PENDING);
        row.setReviewNote(null);
        row.setReviewedBy(null);
        row.setReviewedAt(null);
        row = feedback.save(row);
        log.info("ASSISTANT_FEEDBACK_SUBMITTED feedbackId={} conversationId={} messageId={} rating={} created={}", row.getId(), id,
                answer.getId(), row.getRating(), created);
        return new AssistantFeedbackResponse(answer.getId(), row.getRating(), row.getComment(), row.getSubmittedAt());
    }

    // ---- Admin -----------------------------------------------------------------------------------------------------

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true)
    public AdminFeedbackPageResponse list(AuthenticatedUser actor, int page, int size, String search, String rating,
            String reviewStatus, String from, String to) {
        AdminAssistantHistoryServiceImpl.requirePage(page, size, MAX_PAGE_SIZE);
        FeedbackFilter filter = filter(search, rating, reviewStatus, from, to);
        long total = queries.countFeedback(filter);
        var rows = total == 0 ? List.<FeedbackRow>of() : queries.findFeedback(filter, size, (long) page * size, PREVIEW_CHARS + 1);
        int totalPages = (int) Math.ceil((double) total / size);
        return new AdminFeedbackPageResponse(rows.stream().map(r -> response(r, true)).toList(), page, size, total, totalPages,
                page == 0, page >= totalPages - 1);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true)
    public AdminFeedbackStatisticsResponse statistics(AuthenticatedUser actor) {
        var totals = queries.feedbackTotals();
        return new AdminFeedbackStatisticsResponse(totals.total(), totals.positive(), totals.negative(), totals.awaitingReview());
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true)
    public AdminFeedbackResponse detail(AuthenticatedUser actor, long id) {
        if (id < 1) throw AiAssistantFailure.invalidMessage();
        log.info("ADMIN_ASSISTANT_FEEDBACK_READ actorUserId={} feedbackId={}", actor.userId(), id);
        return response(queries.findFeedback(id).orElseThrow(AiAssistantFailure::feedbackNotFound), false);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional
    public AdminFeedbackResponse review(AuthenticatedUser actor, long id, AdminFeedbackReviewRequest request) {
        if (id < 1 || request == null || !REVIEW_STATUSES.contains(request.reviewStatus())
                || request.reviewNote() != null && request.reviewNote().length() > 1000)
            throw AiAssistantFailure.invalidMessage();
        AssistantMessageFeedback row = feedback.findById(id).orElseThrow(AiAssistantFailure::feedbackNotFound);
        boolean reopened = AssistantMessageFeedback.PENDING.equals(request.reviewStatus());
        row.setReviewStatus(request.reviewStatus());
        row.setReviewNote(request.reviewNote() == null || request.reviewNote().isBlank() ? null : request.reviewNote().strip());
        // Back to "awaiting review": nobody has reviewed it in its current state.
        row.setReviewedBy(reopened ? null : actor.userId());
        row.setReviewedAt(reopened ? null : clock.instant());
        feedback.saveAndFlush(row);
        log.info("ADMIN_ASSISTANT_FEEDBACK_REVIEWED actorUserId={} feedbackId={} reviewStatus={}", actor.userId(), id, row.getReviewStatus());
        return response(queries.findFeedback(id).orElseThrow(AiAssistantFailure::feedbackNotFound), false);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ACADEMIC_ADMIN')")
    @Transactional(readOnly = true)
    public byte[] exportCsv(AuthenticatedUser actor, String search, String rating, String reviewStatus, String from, String to) {
        var rows = queries.findFeedback(filter(search, rating, reviewStatus, from, to), MAX_EXPORT_ROWS, 0, 100_000);
        var lines = new ArrayList<List<String>>(rows.size());
        int number = 1;
        for (FeedbackRow r : rows)
            lines.add(Arrays.asList(String.valueOf(number++), r.studentCode(), r.studentName(), r.question(), r.answer(), r.rating(),
                    r.comment(), String.valueOf(r.submittedAt()), r.reviewStatus(), r.reviewNote(), r.reviewerName(),
                    r.reviewedAt() == null ? null : r.reviewedAt().toString(), r.conversationId().toString()));
        log.info("ADMIN_ASSISTANT_FEEDBACK_EXPORT actorUserId={} rows={}", actor.userId(), rows.size());
        return AssistantCsv.write(List.of("No.", "Student Code", "Student Name", "Question", "AI Response", "Rating", "Comment",
                "Submitted At (UTC)", "Review Status", "Review Note", "Reviewed By", "Reviewed At (UTC)", "Conversation ID"), lines);
    }

    private static FeedbackFilter filter(String search, String rating, String reviewStatus, String from, String to) {
        String ratingValue = blankToNull(rating);
        String statusValue = blankToNull(reviewStatus);
        if (ratingValue != null && !RATINGS.contains(ratingValue) || statusValue != null && !REVIEW_STATUSES.contains(statusValue))
            throw AiAssistantFailure.invalidMessage();
        var filter = new FeedbackFilter(AdminAssistantHistoryServiceImpl.search(search), ratingValue, statusValue,
                AdminAssistantHistoryServiceImpl.instant(from), AdminAssistantHistoryServiceImpl.instant(to));
        AdminAssistantHistoryServiceImpl.requireRange(filter.from(), filter.to());
        return filter;
    }

    private AdminFeedbackResponse response(FeedbackRow r, boolean preview) {
        return new AdminFeedbackResponse(r.id(), r.rating(), r.comment(), r.reviewStatus(), r.reviewNote(),
                r.reviewerId() == null ? null : new AdminFeedbackResponse.Reviewer(r.reviewerId(), r.reviewerName()), r.reviewedAt(),
                r.submittedAt(), new AssistantStudentReference(r.studentId(), r.studentCode(), r.studentName()),
                r.conversationId().toString(), r.conversationTitle(), r.messageId(),
                preview ? shorten(r.question()) : r.question(), preview ? shorten(r.answer()) : r.answer(),
                preview ? null : r.model(), preview ? List.of() : mapper.sources(r.sources()));
    }

    // The list query returns one character more than the preview, so a cut text is recognisable here.
    private static String shorten(String text) {
        if (text == null || text.length() <= PREVIEW_CHARS) return text;
        int end = Character.isHighSurrogate(text.charAt(PREVIEW_CHARS - 1)) ? PREVIEW_CHARS - 1 : PREVIEW_CHARS;
        return text.substring(0, end).stripTrailing() + "…";
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
