package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantHistoryProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantConversation;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessage;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AssistantConversationMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantConversationHistoryRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantConversationRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantMessageRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantConversationHistoryService;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.util.*;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL conversation history. Only ids, counts and versions are logged, never a title, a preview or message text.
 * Stored text is the Student's message and the final answer they received; tool calls, tool results, passages and
 * prompts are never written here.
 */
@Service
public class AssistantConversationHistoryServiceImpl implements AssistantConversationHistoryService {
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_MESSAGE_LIMIT = 100;
    private static final Logger log = LoggerFactory.getLogger(AssistantConversationHistoryServiceImpl.class);

    private final AssistantConversationHistoryRepository conversations;
    private final AssistantMessageRepository messages;
    private final AssistantConversationRepository shortTerm;
    private final AssistantConversationMapper mapper;
    private final AiAssistantHistoryProperties properties;
    private final CurrentStudentProvider currentStudent;
    private final StudentAcademicService academics;

    public AssistantConversationHistoryServiceImpl(AssistantConversationHistoryRepository conversations,
            AssistantMessageRepository messages, AssistantConversationRepository shortTerm, AssistantConversationMapper mapper,
            AiAssistantHistoryProperties properties, CurrentStudentProvider currentStudent, StudentAcademicService academics) {
        this.conversations = conversations;
        this.messages = messages;
        this.shortTerm = shortTerm;
        this.mapper = mapper;
        this.properties = properties;
        this.currentStudent = currentStudent;
        this.academics = academics;
    }

    @Override
    public boolean enabled() { return properties.enabled(); }

    @Override
    @Transactional
    public AssistantConversationContext recordTurn(AssistantConversationContext previous, ConversationMessage userMessage,
            ConversationMessage assistantMessage, AssistantTurnDetails details,
            Supplier<AssistantConversationContext> shortTermSave) {
        UUID id = UUID.fromString(previous.conversationId());
        AssistantTurnDetails turn = details == null ? AssistantTurnDetails.NONE : details;
        AssistantConversation conversation = conversations.findByIdForUpdate(id).orElse(null);
        boolean created = conversation == null;
        if (created) {
            // The first stored turn. Also a conversation that so far lived only in Redis (started before Step 17 or
            // while history was off): its row starts here, with this turn's question as the title.
            var row = new AssistantConversation();
            row.setId(id);
            row.setStudentId(previous.studentId());
            row.setTitle(AssistantConversationMapper.title(userMessage.content()));
            row.setCreatedAt(previous.isNew() ? userMessage.createdAt() : previous.createdAt());
            row.setUpdatedAt(userMessage.createdAt());
            conversation = conversations.saveAndFlush(row);
        } else if (!conversation.getStudentId().equals(previous.studentId())) {
            throw AiAssistantFailure.conversationNotFound();
        }
        messages.saveAll(List.of(
                message(id, userMessage, turn.requestId(), null, null),
                message(id, assistantMessage, turn.requestId(), turn.model(), mapper.sourcesJson(turn.sources()))));

        AssistantConversationContext saved = shortTermSave.get();

        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setLastMessagePreview(AssistantConversationMapper.preview(assistantMessage.content()));
        conversation.setContextVersion(saved.version());
        conversation.setAcademicReference(mapper.referenceJson(saved.academicReference()));
        conversation.setUpdatedAt(saved.updatedAt());
        log.info("ASSISTANT_HISTORY_SAVED conversationId={} created={} messages={} version={}", id, created,
                conversation.getMessageCount(), saved.version());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssistantConversationContext> restoreContext(String conversationId, long studentId, int maxTurns) {
        if (!properties.enabled()) return Optional.empty();
        UUID id = UUID.fromString(conversationId);
        AssistantConversation conversation = conversations.findByIdAndStudentId(id, studentId).orElse(null);
        if (conversation == null) return Optional.empty();
        var newest = new ArrayList<>(messages.findNewest(id, PageRequest.of(0, maxTurns * 2)));
        Collections.reverse(newest);
        // Turns are stored as USER/ASSISTANT pairs; a window that starts with an answer has lost its question.
        if (!newest.isEmpty() && !ConversationMessage.USER.equals(newest.getFirst().getRole())) newest.removeFirst();
        var history = newest.stream()
                .map(m -> new ConversationMessage(m.getRole(), m.getContent(), m.getCreatedAt())).toList();
        return Optional.of(new AssistantConversationContext(AssistantConversationContext.SCHEMA_VERSION, conversationId, studentId,
                conversation.getCreatedAt(), conversation.getUpdatedAt(), Math.max(1, conversation.getContextVersion()), history,
                mapper.reference(conversation.getAcademicReference())));
    }

    @Override
    @Transactional(readOnly = true)
    public AssistantConversationPageResponse listMyConversations(int page, int size) {
        long studentId = student();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE)
            throw AiAssistantFailure.invalidMessage();
        if (!properties.enabled()) return new AssistantConversationPageResponse(List.of(), page, size, 0, 0, true, true);
        var result = conversations.findByStudentId(studentId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
        return new AssistantConversationPageResponse(result.getContent().stream().map(mapper::toSummary).toList(), page, size,
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public AssistantConversationDetailResponse getMyConversation(String conversationId, int limit, Long beforeMessageId) {
        long studentId = student();
        UUID id = UUID.fromString(ConversationContextServiceImpl.normalizeId(conversationId));
        if (limit < 1 || limit > MAX_MESSAGE_LIMIT || beforeMessageId != null && beforeMessageId < 1)
            throw AiAssistantFailure.invalidMessage();
        AssistantConversation conversation = owned(conversations.findByIdAndStudentId(id, studentId), id);
        // One more than asked for tells whether older messages exist.
        var limitPlusOne = PageRequest.of(0, limit + 1);
        var newest = new ArrayList<>(beforeMessageId == null ? messages.findNewest(id, limitPlusOne)
                : messages.findNewestBefore(id, beforeMessageId, limitPlusOne));
        boolean hasMoreBefore = newest.size() > limit;
        if (hasMoreBefore) newest.removeLast();
        Collections.reverse(newest);
        return mapper.toDetail(conversation, newest, hasMoreBefore);
    }

    @Override
    @Transactional
    public void deleteMyConversation(String conversationId) {
        long studentId = student();
        String canonical = ConversationContextServiceImpl.normalizeId(conversationId);
        UUID id = UUID.fromString(canonical);
        // The row lock makes a turn that is being stored finish (or fail) before the conversation disappears.
        AssistantConversation conversation = owned(properties.enabled() ? conversations.findByIdForUpdate(id)
                .filter(c -> c.getStudentId().equals(studentId)) : Optional.empty(), id);
        try {
            // Redis first: if it cannot be reached nothing is deleted, so no left-over context can revive the conversation.
            shortTerm.delete(canonical);
        } catch (DataAccessException e) {
            log.warn("ASSISTANT_CONTEXT_UNAVAILABLE operation=delete conversationId={} error={}", id, e.getClass().getSimpleName());
            throw AiAssistantFailure.contextUnavailable();
        }
        int stored = messages.deleteByConversationId(id);
        conversations.delete(conversation);
        log.info("ASSISTANT_CONVERSATION_DELETED conversationId={} messages={}", id, stored);
    }

    // 401/403/PASSWORD_CHANGE_REQUIRED from the token, then the same eligibility check as the chat endpoint.
    private long student() {
        long studentId = currentStudent.getCurrentStudentId();
        academics.requireEligibleStudent(studentId);
        return studentId;
    }

    private AssistantConversation owned(Optional<AssistantConversation> found, UUID id) {
        if (!properties.enabled() || found.isEmpty()) {
            log.info("ASSISTANT_CONVERSATION_NOT_FOUND conversationId={} reason=history", id);
            throw AiAssistantFailure.conversationNotFound();
        }
        return found.get();
    }

    private static AssistantMessage message(UUID conversationId, ConversationMessage source, String requestId, String model,
            String sources) {
        var message = new AssistantMessage();
        message.setConversationId(conversationId);
        message.setRole(source.role());
        message.setContent(source.content() == null ? "" : source.content());
        message.setRequestId(requestId);
        message.setModel(model == null || model.length() <= 100 ? model : model.substring(0, 100));
        message.setSources(sources);
        message.setCreatedAt(source.createdAt());
        return message;
    }
}
