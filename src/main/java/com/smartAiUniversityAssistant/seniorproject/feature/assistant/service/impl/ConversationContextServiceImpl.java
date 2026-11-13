package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantContextProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantConversationRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantConversationHistoryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.ConversationContextService;
import java.time.Clock;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/**
 * Redis-backed conversation context. Only conversation ids, versions, counts and sizes are logged, never message text.
 * Trimming keeps the newest complete turns: first to AI_CONTEXT_MAX_TURNS pairs, then oldest pairs are dropped until
 * the JSON fits AI_CONTEXT_MAX_BYTES, and only if the newest pair alone is still too large is its text shortened.
 * The academic reference is stored separately from the messages, so trimming never invents or loses a referent.
 * <p>
 * Step 17: with a history service, every saved turn is also written to PostgreSQL (the Redis save runs inside that
 * transaction) and a conversation missing in Redis is restored from PostgreSQL by the same trimming rules.
 */
@Service
public class ConversationContextServiceImpl implements ConversationContextService {
    /** A stored message is at most this long; the student's own message is already limited to 4000 characters. */
    static final int MAX_STORED_MESSAGE_CHARS = 4000;
    private static final String TRUNCATED = " …";
    private static final Pattern UUID_V4 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Logger log = LoggerFactory.getLogger(ConversationContextServiceImpl.class);

    private final AssistantConversationRepository repository;
    private final AiAssistantContextProperties properties;
    private final Clock clock;
    /** Null for a service built without history (Step 10 behaviour; existing unit tests). */
    private final AssistantConversationHistoryService history;

    /** Redis only: nothing is stored in or restored from PostgreSQL. */
    public ConversationContextServiceImpl(AssistantConversationRepository repository,
            AiAssistantContextProperties properties, Clock clock) {
        this(repository, properties, clock, null);
    }

    @Autowired
    public ConversationContextServiceImpl(AssistantConversationRepository repository,
            AiAssistantContextProperties properties, Clock clock, AssistantConversationHistoryService history) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.history = history;
    }

    @Override
    public boolean enabled() { return properties.enabled(); }

    @Override
    public AssistantConversationContext createNew(long studentId) {
        var now = clock.instant();
        return new AssistantConversationContext(AssistantConversationContext.SCHEMA_VERSION, UUID.randomUUID().toString(),
                studentId, now, now, 0, List.of(), AcademicConversationReference.EMPTY);
    }

    @Override
    public AssistantConversationContext getOwnedConversation(String conversationId, long studentId) {
        String id = normalizeId(conversationId);
        if (!properties.enabled()) throw AiAssistantFailure.conversationNotFound();
        Optional<AssistantConversationContext> found;
        try {
            found = repository.findById(id);
        } catch (DataAccessException e) {
            log.warn("ASSISTANT_CONTEXT_UNAVAILABLE operation=load error={}", e.getClass().getSimpleName());
            throw AiAssistantFailure.contextUnavailable();
        }
        // Step 17: Redis no longer has it (TTL), but the Student's stored conversation can be continued.
        if (found.isEmpty() && historyEnabled()) found = restore(id, studentId);
        // Another student's conversation is reported exactly like a missing one, and its contents are never read further.
        if (found.isEmpty() || found.get().studentId() != studentId) {
            log.info("ASSISTANT_CONVERSATION_NOT_FOUND conversationId={} reason={}", id, found.isEmpty() ? "missing" : "owner");
            throw AiAssistantFailure.conversationNotFound();
        }
        return found.get();
    }

    @Override
    public AssistantConversationContext saveCompletedTurn(AssistantConversationContext context, ConversationMessage userMessage,
            ConversationMessage assistantMessage, AcademicConversationReference reference, AssistantTurnDetails details) {
        if (!historyEnabled()) return saveShortTerm(context, userMessage, assistantMessage, reference);
        try {
            // PostgreSQL first, Redis inside its transaction: a Redis conflict or failure rolls the stored turn back.
            return history.recordTurn(context, userMessage, assistantMessage, details,
                    () -> saveShortTerm(context, userMessage, assistantMessage, reference));
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // Two turns of one conversation at the same moment (or its deletion): the same answer as the Redis check gives.
            log.warn("ASSISTANT_CONVERSATION_CONFLICT conversationId={} source=history error={}", context.conversationId(),
                    e.getClass().getSimpleName());
            throw AiAssistantFailure.conversationConflict();
        } catch (DataAccessException | TransactionException e) {
            log.warn("ASSISTANT_HISTORY_UNAVAILABLE operation=save conversationId={} error={}", context.conversationId(),
                    e.getClass().getSimpleName());
            throw AiAssistantFailure.historyUnavailable();
        }
    }

    private boolean historyEnabled() { return history != null && history.enabled(); }

    // Rebuilds the Redis value of a stored conversation. Create-if-absent: when another request restored it at the same
    // moment, that value is the one both continue with (the version check then lets only one of them save).
    private Optional<AssistantConversationContext> restore(String id, long studentId) {
        Optional<AssistantConversationContext> stored;
        try {
            stored = history.restoreContext(id, studentId, properties.maxTurns());
        } catch (DataAccessException | TransactionException e) {
            log.warn("ASSISTANT_HISTORY_UNAVAILABLE operation=restore conversationId={} error={}", id, e.getClass().getSimpleName());
            throw AiAssistantFailure.historyUnavailable();
        }
        if (stored.isEmpty()) return stored;
        var source = stored.get();
        var restored = fitToSize(withMessages(source, source.messages().stream().map(m -> cap(m, MAX_STORED_MESSAGE_CHARS)).toList()));
        restored = new AssistantConversationContext(restored.schemaVersion(), restored.conversationId(), restored.studentId(),
                restored.createdAt(), restored.updatedAt(), restored.version(), restored.messages(), bounded(restored.academicReference()));
        try {
            if (!repository.create(restored, properties.ttl())) return repository.findById(id);
        } catch (DataAccessException e) {
            log.warn("ASSISTANT_CONTEXT_UNAVAILABLE operation=restore conversationId={} error={}", id, e.getClass().getSimpleName());
            throw AiAssistantFailure.contextUnavailable();
        }
        log.info("ASSISTANT_CONVERSATION_RESTORED conversationId={} version={} messages={}", id, restored.version(), restored.messages().size());
        return Optional.of(restored);
    }

    // The Step 10 save: Redis only.
    private AssistantConversationContext saveShortTerm(AssistantConversationContext context, ConversationMessage userMessage,
            ConversationMessage assistantMessage, AcademicConversationReference reference) {
        var messages = new ArrayList<>(context.messages());
        messages.add(cap(userMessage, MAX_STORED_MESSAGE_CHARS));
        messages.add(cap(assistantMessage, MAX_STORED_MESSAGE_CHARS));
        int maxMessages = properties.maxTurns() * 2;
        while (messages.size() > maxMessages) messages.subList(0, 2).clear();
        var next = new AssistantConversationContext(AssistantConversationContext.SCHEMA_VERSION, context.conversationId(),
                context.studentId(), context.createdAt(), clock.instant(), context.version() + 1, messages, bounded(reference));
        next = fitToSize(next);

        try {
            if (context.isNew()) {
                if (!repository.create(next, properties.ttl())) throw AiAssistantFailure.conversationConflict();
            } else {
                switch (repository.saveIfVersionMatches(next, context.version(), properties.ttl())) {
                    case SAVED -> { }
                    case CONFLICT -> {
                        log.warn("ASSISTANT_CONVERSATION_CONFLICT conversationId={} expectedVersion={}", context.conversationId(), context.version());
                        throw AiAssistantFailure.conversationConflict();
                    }
                    case MISSING -> {
                        log.info("ASSISTANT_CONVERSATION_NOT_FOUND conversationId={} reason=expired-before-save", context.conversationId());
                        throw AiAssistantFailure.conversationNotFound();
                    }
                }
            }
        } catch (DataAccessException | IllegalStateException e) {
            log.warn("ASSISTANT_CONTEXT_UNAVAILABLE operation=save conversationId={} error={}", context.conversationId(), e.getClass().getSimpleName());
            throw AiAssistantFailure.contextUnavailable();
        }
        log.info("ASSISTANT_CONVERSATION_SAVED conversationId={} version={} messages={} bytes={} ttlMinutes={}",
                next.conversationId(), next.version(), next.messages().size(), repository.serializedBytes(next), properties.ttlMinutes());
        return next;
    }

    // Only the canonical lowercase form the server generates is accepted, so one conversation has exactly one key.
    static String normalizeId(String conversationId) {
        if (conversationId == null) throw AiAssistantFailure.invalidConversationId();
        String id = conversationId.strip();
        if (!UUID_V4.matcher(id).matches()) throw AiAssistantFailure.invalidConversationId();
        return id;
    }

    private AcademicConversationReference bounded(AcademicConversationReference reference) {
        if (reference == null) return AcademicConversationReference.EMPTY;
        var courses = reference.lastPresentedCourses();
        if (courses.size() <= properties.maxCourseReferences()) return reference;
        return new AcademicConversationReference(reference.activeCourseCode(), reference.activeCourseSectionId(),
                reference.lastAcademicIntent(), courses.subList(0, properties.maxCourseReferences()));
    }

    private AssistantConversationContext fitToSize(AssistantConversationContext context) {
        var messages = new ArrayList<>(context.messages());
        var current = context;
        while (repository.serializedBytes(current) > properties.maxBytes() && messages.size() > 2) {
            messages.subList(0, 2).clear();
            current = withMessages(current, messages);
        }
        // Only the newest pair is left and it is still too large: shorten the answer first, then the question.
        int limit = MAX_STORED_MESSAGE_CHARS;
        while (repository.serializedBytes(current) > properties.maxBytes() && limit > 64) {
            limit /= 2;
            int index = messages.size() - 1;
            messages.set(index, cap(messages.get(index), limit));
            if (repository.serializedBytes(withMessages(current, messages)) > properties.maxBytes() && index > 0)
                messages.set(index - 1, cap(messages.get(index - 1), limit));
            current = withMessages(current, messages);
        }
        return current;
    }

    private static AssistantConversationContext withMessages(AssistantConversationContext c, List<ConversationMessage> messages) {
        return new AssistantConversationContext(c.schemaVersion(), c.conversationId(), c.studentId(), c.createdAt(),
                c.updatedAt(), c.version(), messages, c.academicReference());
    }

    private static ConversationMessage cap(ConversationMessage message, int maxChars) {
        String content = message.content() == null ? "" : message.content();
        if (content.length() <= maxChars) return message;
        int end = maxChars - TRUNCATED.length();
        if (Character.isHighSurrogate(content.charAt(end - 1))) end--;
        return new ConversationMessage(message.role(), content.substring(0, end) + TRUNCATED, message.createdAt());
    }
}
