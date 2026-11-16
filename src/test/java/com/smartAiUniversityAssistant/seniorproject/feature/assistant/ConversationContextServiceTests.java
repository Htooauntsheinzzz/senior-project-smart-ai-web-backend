package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantContextProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantConversationRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository.AssistantConversationRepository.SaveResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.ConversationContextServiceImpl;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import tools.jackson.databind.json.JsonMapper;

/** Conversation rules with the Redis repository mocked: failures, ownership, versions and trimming. */
class ConversationContextServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
    private final AssistantConversationRepository repository = mock(AssistantConversationRepository.class);
    private final AiAssistantContextProperties properties = new AiAssistantContextProperties(true, 30, 6, 20, 32768);
    private final ConversationContextServiceImpl service =
            new ConversationContextServiceImpl(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    private final AssistantConversationRepository sizing = new AssistantConversationRepository(null, JsonMapper.builder().build());

    {
        when(repository.serializedBytes(any())).thenAnswer(i -> sizing.serializedBytes(i.getArgument(0)));
    }

    @Test
    void newConversationsGetUnpredictableIdsAndAreStoredOnlyWhenATurnCompletes() {
        var a = service.createNew(15L);
        var b = service.createNew(15L);
        assertThat(a.conversationId()).isNotEqualTo(b.conversationId());
        assertThat(UUID.fromString(a.conversationId()).version()).isEqualTo(4);
        assertThat(a.version()).isZero();
        verifyNoInteractions(repository);

        when(repository.create(any(), any())).thenReturn(true);
        var saved = service.saveCompletedTurn(a, ConversationMessage.user("Hi", NOW), ConversationMessage.assistant("Hello", NOW), null);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.messages()).hasSize(2);
        verify(repository).create(saved, Duration.ofMinutes(30));
    }

    @Test
    void ownershipFormatAndRedisFailuresMapToSafeErrors() {
        String id = UUID.randomUUID().toString();
        var owned = context(id, 15L, 3, List.of());
        when(repository.findById(id)).thenReturn(Optional.of(owned));
        assertThat(service.getOwnedConversation(" " + id + " ", 15L)).isEqualTo(owned);
        expect(() -> service.getOwnedConversation(id, 16L), 404, "CONVERSATION_NOT_FOUND");
        expect(() -> service.getOwnedConversation(UUID.randomUUID().toString(), 15L), 404, "CONVERSATION_NOT_FOUND");
        expect(() -> service.getOwnedConversation("abc", 15L), 400, "INVALID_CONVERSATION_ID");

        when(repository.findById(id)).thenThrow(new RedisConnectionFailureException("down"));
        expect(() -> service.getOwnedConversation(id, 15L), 503, "ASSISTANT_CONTEXT_UNAVAILABLE");
        when(repository.saveIfVersionMatches(any(), anyLong(), any())).thenThrow(new RedisConnectionFailureException("down"));
        expect(() -> service.saveCompletedTurn(owned, user("q"), assistant("a"), null), 503, "ASSISTANT_CONTEXT_UNAVAILABLE");
    }

    @Test
    void savesCheckTheLoadedVersion() {
        var loaded = context(UUID.randomUUID().toString(), 15L, 3, List.of());
        when(repository.saveIfVersionMatches(any(), eq(3L), any())).thenReturn(SaveResult.SAVED, SaveResult.CONFLICT, SaveResult.MISSING);
        assertThat(service.saveCompletedTurn(loaded, user("q"), assistant("a"), null).version()).isEqualTo(4);
        expect(() -> service.saveCompletedTurn(loaded, user("q"), assistant("a"), null), 409, "CONVERSATION_CONFLICT");
        expect(() -> service.saveCompletedTurn(loaded, user("q"), assistant("a"), null), 404, "CONVERSATION_NOT_FOUND");
        when(repository.create(any(), any())).thenReturn(false);
        expect(() -> service.saveCompletedTurn(service.createNew(15L), user("q"), assistant("a"), null), 409, "CONVERSATION_CONFLICT");
    }

    @Test
    void oldTurnsAreTrimmedToTheTurnAndByteLimitsKeepingTheReference() {
        var messages = new ArrayList<ConversationMessage>();
        for (int i = 0; i < 12; i++) messages.add(i % 2 == 0 ? user("q" + i) : assistant("a" + i));
        var loaded = context(UUID.randomUUID().toString(), 15L, 6, messages);
        var reference = new AcademicConversationReference("CS301", 1501L, "get_my_courses", courses(30));
        when(repository.saveIfVersionMatches(any(), anyLong(), any())).thenReturn(SaveResult.SAVED);

        var saved = service.saveCompletedTurn(loaded, user("newest"), assistant("answer"), reference);
        assertThat(saved.messages()).hasSize(12);
        assertThat(saved.messages().getFirst().content()).isEqualTo("q2");
        assertThat(saved.messages().getLast().content()).isEqualTo("answer");
        assertThat(saved.academicReference().lastPresentedCourses()).hasSize(20);
        assertThat(saved.academicReference().activeCourseCode()).isEqualTo("CS301");

        // A tight byte budget drops whole old pairs first, then shortens only the newest pair.
        var tight = new ConversationContextServiceImpl(repository, new AiAssistantContextProperties(true, 30, 6, 20, 4096),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var big = new ArrayList<ConversationMessage>();
        for (int i = 0; i < 6; i++) big.add(i % 2 == 0 ? user("q".repeat(900)) : assistant("a".repeat(900)));
        var huge = tight.saveCompletedTurn(context(UUID.randomUUID().toString(), 15L, 3, big), user("ข".repeat(4000)),
                assistant("ဖ".repeat(6000)), reference);
        assertThat(sizing.serializedBytes(huge)).isLessThanOrEqualTo(4096);
        assertThat(huge.messages()).hasSize(2);
        assertThat(huge.messages().get(0).content()).startsWith("ข").endsWith(" …");
        assertThat(huge.messages().get(1).content()).startsWith("ဖ").endsWith(" …");
        assertThat(huge.academicReference().activeCourseCode()).isEqualTo("CS301");
    }

    @Test
    void disabledContextHasNoConversations() {
        var off = new ConversationContextServiceImpl(repository, new AiAssistantContextProperties(false, 30, 6, 20, 32768),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(off.enabled()).isFalse();
        expect(() -> off.getOwnedConversation(UUID.randomUUID().toString(), 15L), 404, "CONVERSATION_NOT_FOUND");
        verifyNoInteractions(repository);
    }

    @Test
    void storedJsonIsTypedAndRoundTrips() {
        var mapper = JsonMapper.builder().build();
        var original = context("9fa36c31-f92b-4d1e-9f4e-217d25362010", 15L, 2, List.of(user("What courses do I have?")));
        String raw = sizing.serialize(original);
        assertThat(raw).contains("\"schemaVersion\":1").contains("\"studentId\":15").doesNotContain("\"new\"");
        assertThat(mapper.readValue(raw, AssistantConversationContext.class)).isEqualTo(original);
    }

    private static List<PresentedCourseReference> courses(int n) {
        var list = new ArrayList<PresentedCourseReference>();
        for (int i = 1; i <= n; i++) list.add(new PresentedCourseReference(i, "CS" + (100 + i), (long) i));
        return list;
    }

    private static AssistantConversationContext context(String id, long owner, long version, List<ConversationMessage> messages) {
        return new AssistantConversationContext(1, id, owner, NOW, NOW, version, messages, AcademicConversationReference.EMPTY);
    }

    private static ConversationMessage user(String text) { return ConversationMessage.user(text, NOW); }
    private static ConversationMessage assistant(String text) { return ConversationMessage.assistant(text, NOW); }

    private static void expect(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, int status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AiAssistantFailure.class, e -> {
            assertThat(e.status()).isEqualTo(status);
            assertThat(e.code()).isEqualTo(code);
        });
    }
}
