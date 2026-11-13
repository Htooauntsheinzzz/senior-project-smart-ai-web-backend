package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicProgressQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/** Spec file 57, section 18.5: the hybrid flow with a scripted model, a mocked progress query and a fake search. */
@ExtendWith(OutputCaptureExtension.class)
class StudentAssistantHybridServiceTests {
    static final String QUESTION = "How many credits do I still need to graduate?";
    static final String PROGRESS = "get_my_academic_progress";
    static final String C0 = "Section 1 Program Overview\nA student must complete at least 132 credits to graduate from this program.";
    static final String GOOD = "Your program's curriculum requires at least 132 credits (Sample B.Sc. Computer Science Curriculum 2026, "
            + "Section 1). You have earned 18 credits from recorded course results, so at least 114 more credits remain. If you pass the 9 "
            + "credits you are taking now, at least 105 more credits will remain. The registrar's office can confirm your remaining "
            + "requirements with an official degree audit.";
    final CurrentStudentProvider currentStudent = mock(CurrentStudentProvider.class);
    final StudentAcademicService academics = mock(StudentAcademicService.class);
    final AcademicQueryService academicQuery = mock(AcademicQueryService.class);
    final AcademicProgressQueryService progressQuery = mock(AcademicProgressQueryService.class);
    final KnowledgeSearchService search = mock(KnowledgeSearchService.class);
    final LlmClient llm = mock(LlmClient.class);
    final ConversationContextService conversations = mock(ConversationContextService.class);
    final AiAssistantProperties properties = new AiAssistantProperties("test-provider", "test-only-key", "configured-model",
            "https://provider.example.test/v1", 30, 10, 0.2, 1024, 1, Duration.ZERO, 4, 8, 120);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-10T08:00:00Z"), ZoneOffset.UTC);
    StudentAssistantServiceImpl service = service(AssistantKnowledgeSettings.active(4, 8000, 2), AssistantProgressSettings.of(true, true));

    StudentAssistantServiceImpl service(AssistantKnowledgeSettings knowledge, AssistantProgressSettings progress) {
        var registry = new AcademicAiToolRegistryImpl(knowledge, progress);
        var executor = new AcademicAiToolExecutorImpl(registry, academicQuery, new AcademicAiToolResultMapper(), search, knowledge,
                progressQuery, progress);
        var policy = new AcademicGroundingPolicyImpl();
        var responses = new SafeAcademicResponseFactoryImpl();
        return new StudentAssistantServiceImpl(currentStudent, academics, llm, registry, executor, properties, conversations,
                new AcademicReferenceResolverImpl(), policy, new AcademicGroundingServiceImpl(policy, responses, clock),
                new GroundedAnswerValidatorImpl(), responses, clock, knowledge, progress);
    }

    @BeforeEach
    void setUp() {
        when(currentStudent.getCurrentStudentId()).thenReturn(905_317L);
        when(academicQuery.getMyAcademicProfile()).thenReturn(KnowledgeSearchToolTests.profile(11L, 22L));
        when(progressQuery.getMyAcademicProgress()).thenReturn(new AcademicProgressQueryResult(new BigDecimal("2.57"), new BigDecimal("18"),
                3, 9, 3, 2026, "AVAILABLE"));
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(new KnowledgeSearchHit(1, 0.9, 1,
                "Sample B.Sc. Computer Science Curriculum 2026", "Curriculum", "2026-2027", "Section 1 Program Overview", "TEXT", 1, 1, C0,
                KnowledgeAudience.PROGRAM, false)));
    }

    void model(Object... turns) {
        var stubbing = when(llm.chat(any()));
        for (Object turn : turns)
            stubbing = stubbing.thenReturn(turn instanceof String text ? new LlmResponse(text, "scripted-model", null)
                    : new LlmResponse(null, "scripted-model", null, List.of((LlmToolCall) turn), "o"));
    }

    static LlmToolCall searchCall() { return new LlmToolCall("s", "search_university_knowledge", Map.of("query", "credits required to graduate")); }

    StudentAssistantChatResponse chat(String message) { return service.chat(new StudentAssistantChatRequest(message)); }

    List<LlmRequest> sent(int n) {
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(n)).chat(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void flow1And14AHybridAnswer(CapturedOutput output) {
        model(searchCall(), GOOD);
        var response = chat(QUESTION);
        assertThat(response.message()).isEqualTo(GOOD);
        assertThat(response.sources()).extracting(StudentAssistantChatResponse.Source::title).containsExactly("Sample B.Sc. Computer Science Curriculum 2026");
        var order = inOrder(progressQuery, llm);
        order.verify(progressQuery).getMyAcademicProgress();
        order.verify(llm, times(2)).chat(any());
        String prompt = sent(2).getFirst().systemPrompt();
        int k = prompt.indexOf("UNIVERSITY DOCUMENT RULES"), p = prompt.indexOf("ACADEMIC PROGRESS RULES"),
                h = prompt.indexOf("PERSONAL STANDING RULES"), b = prompt.indexOf("VERIFIED STUDENT PROGRESS");
        assertThat(k).isPositive();
        assertThat(p).isGreaterThan(k);
        assertThat(h).isGreaterThan(p);
        assertThat(b).isGreaterThan(h);
        assertThat(prompt).endsWith("\ncgpa: 2.57\ncreditsEarned: 18\ncurrentCourses: 3\ncurrentEnrolledCredits: 9\nstudyYear: 3\n"
                + "academicYear: 2026\ndataStatus: AVAILABLE");
        assertThat(sent(2).getFirst().toolRounds()).isEmpty();
        String out = output.getOut();
        assertThat(out).contains("round=0 tool=get_my_academic_progress success=true").contains("toolCalls=2")
                .contains("ASSISTANT_HYBRID requestId=" + response.requestId() + " progress=VERIFIED dataStatus=AVAILABLE searches=1 knowledge=VERIFIED outcome=ANSWER");
        assertThat(out.substring(out.indexOf("ASSISTANT_HYBRID"))).doesNotContain("2.57");
        assertThat(out.split("ASSISTANT_HYBRID", -1)).hasSize(2);
    }

    @Test
    void flow2ProgressFails() {
        when(progressQuery.getMyAcademicProgress()).thenThrow(new DataAccessResourceFailureException("down"));
        var response = chat(QUESTION);
        assertThat(response.message()).isEqualTo(new SafeAcademicResponseFactoryImpl().academicServiceUnavailable());
        assertThat(response.sources()).isEmpty();
        verifyNoInteractions(llm, search);
    }

    @Test
    void flow3And4NothingFoundOrFailedSearch() {
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of());
        model(searchCall());
        var none = chat(QUESTION);
        assertThat(none.message()).startsWith("Your academic progress:").endsWith(SafeAcademicResponseFactoryImpl.KNOWLEDGE_NOT_FOUND);
        assertThat(none.sources()).isEmpty();
        sent(1);
        reset(llm, search);
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenThrow(KnowledgeFailure.searchUnavailable());
        model(searchCall());
        assertThat(chat(QUESTION).message()).startsWith("Your academic progress:").endsWith(SafeAcademicResponseFactoryImpl.KNOWLEDGE_UNAVAILABLE);
    }

    @Test
    void flow5And6NoSearch() {
        model("You need about 114 more credits.", searchCall(), GOOD);
        assertThat(chat(QUESTION).message()).isEqualTo(GOOD);
        var requests = sent(3);
        assertThat(requests.get(1).requireTool()).isTrue();
        assertThat(requests.get(1).systemPrompt()).contains("DOCUMENT SEARCH REQUIRED FOR THIS QUESTION");
        reset(llm);
        model("You need about 114 more credits.", "You need about 114 more credits.");
        assertThat(chat(QUESTION).message()).startsWith("Your academic progress:").endsWith(SafeAcademicResponseFactoryImpl.KNOWLEDGE_MISSING_EVIDENCE);
    }

    @Test
    void flow7And8RejectedAnswers() {
        model(searchCall(), "It requires at least 132 credits. You have earned 18 credits, so at least 104 more credits remain.");
        var wrong = chat(QUESTION);
        assertThat(wrong.message()).startsWith("Your academic progress:").contains("Here is what the university documents say:")
                .endsWith(SafeAcademicResponseFactoryImpl.DEGREE_AUDIT_REFERRAL).doesNotContain("104");
        assertThat(wrong.sources()).hasSize(1);
        reset(llm);
        model(searchCall(), "You have met all graduation requirements.");
        assertThat(chat("Can I graduate this year?").message()).endsWith(SafeAcademicResponseFactoryImpl.DEGREE_AUDIT_REFERRAL);
    }

    @Test
    void flow9And10HybridOff() {
        for (var s : List.of(service(AssistantKnowledgeSettings.active(4, 8000, 2), AssistantProgressSettings.of(true)),
                service(AssistantKnowledgeSettings.inactive(), AssistantProgressSettings.of(true, true)))) {
            reset(llm);
            service = s;
            model(searchCall(), "See the documents.");
            chat(QUESTION);
            assertThat(sent(2)).allSatisfy(r -> assertThat(r.systemPrompt()).doesNotContain("PERSONAL STANDING RULES"));
        }
        verifyNoInteractions(progressQuery);
    }

    @Test
    void flow11ScheduleQuestionFirst() {
        when(academicQuery.getMySchedule()).thenReturn(List.of());
        var response = chat("What is my next class, and how many credits do I still need to graduate?");
        assertThat(response.message()).startsWith(new SafeAcademicResponseFactoryImpl().unsupportedScheduleReasoning());
        verifyNoInteractions(progressQuery, llm);
    }

    @Test
    void flow12TheModelCallsTheProgressToolItself() {
        model(new LlmToolCall("p", PROGRESS, Map.of()), searchCall(), "It requires at least 132 credits. You have earned 21 credits.");
        String reply = chat(QUESTION).message();
        assertThat(reply.split("Your academic progress:", -1)).hasSize(2);
        verify(progressQuery, times(2)).getMyAcademicProgress();
    }

    @Test
    void flow13ConversationAndBlockPerRequest() {
        when(conversations.enabled()).thenReturn(true);
        var context = new AssistantConversationContext(1, "9fa36c31-f92b-4d1e-9f4e-217d25362010", 905_317L, Instant.EPOCH, Instant.EPOCH, 1,
                List.of(ConversationMessage.user("Hi", Instant.EPOCH), ConversationMessage.assistant("Hello", Instant.EPOCH)), null);
        when(conversations.getOwnedConversation(context.conversationId(), 905_317L)).thenReturn(context);
        when(conversations.saveCompletedTurn(any(), any(), any(), any(), any())).thenReturn(context);
        model(searchCall(), GOOD, "Hello again.");
        service.chat(new StudentAssistantChatRequest(QUESTION, context.conversationId()));
        service.chat(new StudentAssistantChatRequest("Thanks", context.conversationId()));
        var user = ArgumentCaptor.forClass(ConversationMessage.class);
        var assistant = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(conversations, times(2)).saveCompletedTurn(any(), user.capture(), assistant.capture(), any(), any());
        assertThat(user.getAllValues().getFirst().content()).isEqualTo(QUESTION);
        assertThat(assistant.getAllValues().getFirst().content()).isEqualTo(GOOD);
        var requests = sent(3);
        assertThat(requests.get(0).systemPrompt()).contains("VERIFIED STUDENT PROGRESS");
        assertThat(requests.get(2).systemPrompt()).doesNotContain("VERIFIED STUDENT PROGRESS");
    }
}
