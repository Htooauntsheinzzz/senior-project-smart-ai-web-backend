package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicProgressQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantKnowledgeSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.ConversationContextService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeSearchService;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

/** Spec file 56, section 17.6: the chat service built by hand with the progress tool and a scripted model. */
class StudentAssistantProgressServiceTests {
    static final String TOOL = "get_my_academic_progress";
    final CurrentStudentProvider currentStudent = mock(CurrentStudentProvider.class);
    final StudentAcademicService academics = mock(StudentAcademicService.class);
    final AcademicQueryService academicQuery = mock(AcademicQueryService.class);
    final AcademicProgressQueryService progressQuery = mock(AcademicProgressQueryService.class);
    final LlmClient llm = mock(LlmClient.class);
    final ConversationContextService conversations = mock(ConversationContextService.class);
    final AiAssistantProperties properties = new AiAssistantProperties("test-provider", "test-only-key", "configured-model",
            "https://provider.example.test/v1", 30, 10, 0.2, 1024, 1, Duration.ZERO, 4, 8, 120);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-10T08:00:00Z"), ZoneOffset.UTC);
    StudentAssistantServiceImpl service = service(AssistantKnowledgeSettings.inactive(), AssistantProgressSettings.of(true));

    StudentAssistantServiceImpl service(AssistantKnowledgeSettings knowledge, AssistantProgressSettings progress) {
        var registry = new AcademicAiToolRegistryImpl(knowledge, progress);
        var executor = new AcademicAiToolExecutorImpl(registry, academicQuery, new AcademicAiToolResultMapper(),
                mock(KnowledgeSearchService.class), knowledge, progressQuery, progress);
        var policy = new AcademicGroundingPolicyImpl();
        var responses = new SafeAcademicResponseFactoryImpl();
        return new StudentAssistantServiceImpl(currentStudent, academics, llm, registry, executor, properties, conversations,
                new AcademicReferenceResolverImpl(), policy, new AcademicGroundingServiceImpl(policy, responses, clock),
                new GroundedAnswerValidatorImpl(), responses, clock, knowledge, progress);
    }

    @BeforeEach
    void setUp() {
        when(currentStudent.getCurrentStudentId()).thenReturn(905_317L);
        when(progressQuery.getMyAcademicProgress()).thenReturn(ProgressToolTests.A);
    }

    void model(Object... turns) {
        var stubbing = when(llm.chat(any()));
        for (Object turn : turns)
            stubbing = stubbing.thenReturn(turn instanceof String text ? new LlmResponse(text, "scripted-model", null)
                    : new LlmResponse(null, "scripted-model", null, List.of(new LlmToolCall("c-" + turn, (String) ((Object[]) turn)[0], Map.of())), "o"));
    }

    static Object[] tool(String name) { return new Object[] {name}; }

    String ask(String message) { return service.chat(new StudentAssistantChatRequest(message)).message(); }

    List<LlmRequest> sent(int n) {
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(n)).chat(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void flow1GpaIsAnsweredFromTheTool() {
        model(tool(TOOL), "Your cumulative GPA is 2.57.");
        assertThat(ask("What is my GPA?")).isEqualTo("Your cumulative GPA is 2.57.");
        var requests = sent(2);
        assertThat(requests.get(0).tools()).hasSize(9);
        assertThat(requests.get(0).tools().get(8).name()).isEqualTo(TOOL);
        assertThat(requests.get(0).systemPrompt()).contains("ACADEMIC PROGRESS RULES").contains("P4. Do not calculate a grade point average")
                .doesNotContain("UNIVERSITY DOCUMENT RULES");
    }

    @Test
    void flow2And4WrongValueOrStandingIsReplaced() {
        model(tool(TOOL), "Your GPA is 3.10.");
        assertThat(ask("What is my GPA?")).startsWith("Your academic progress:").contains("Cumulative GPA: 2.57");
        reset(llm);
        model(tool(TOOL), "Your GPA is 2.57, so you are eligible to graduate.");
        assertThat(ask("Is my GPA good enough?")).startsWith("Your academic progress:");
    }

    @Test
    void flow3NullGpa() {
        when(progressQuery.getMyAcademicProgress()).thenReturn(new AcademicProgressQueryResult(null, null, 3, 9, 3, 2026, "PARTIAL"));
        model(tool(TOOL), "Your GPA is 2.57.");
        assertThat(ask("What is my GPA?")).contains("Cumulative GPA: not currently available");
    }

    @Test
    void flow5DatabaseFailure() {
        when(progressQuery.getMyAcademicProgress()).thenThrow(new DataAccessResourceFailureException("down"));
        model(tool(TOOL), "Your GPA is 2.57.");
        assertThat(ask("What is my GPA?")).isEqualTo(new SafeAcademicResponseFactoryImpl().academicServiceUnavailable());
        sent(1);
    }

    @Test
    void flow6NotActive() {
        service = service(AssistantKnowledgeSettings.inactive(), AssistantProgressSettings.inactive());
        var before = AssistantTestServices.service(currentStudent, academics, llm, mock(com.smartAiUniversityAssistant.seniorproject
                .feature.assistant.service.AcademicAiToolExecutor.class), properties, conversations, clock);
        model("Hello.", "Hello.", tool(TOOL), "Your GPA is 2.57.");
        ask("Hello");
        before.chat(new StudentAssistantChatRequest("Hello"));
        var answer = ask("What is my GPA?");
        var requests = sent(4);
        assertThat(requests.get(0).systemPrompt()).isEqualTo(requests.get(1).systemPrompt()).doesNotContain("ACADEMIC PROGRESS RULES");
        assertThat(requests.get(0).tools()).hasSize(8);
        var result = (Map<?, ?>) requests.get(3).toolRounds().getFirst().results().getFirst().result();
        assertThat(result.get("code")).isEqualTo("UNSUPPORTED_AI_TOOL");
        // As before this step: an ERROR result switches on no GPA claim, so the text stands (Step 11 behaviour).
        assertThat(answer).isEqualTo("Your GPA is 2.57.");
        verifyNoInteractions(progressQuery);
    }

    @Test
    void flow7BothToolsActive() {
        service = service(AssistantKnowledgeSettings.active(4, 8000, 2), AssistantProgressSettings.of(true));
        model("Hello.");
        ask("Hello");
        var request = sent(1).getFirst();
        assertThat(request.tools()).extracting(AcademicAiToolDefinition::name).hasSize(10).endsWith(TOOL, "search_university_knowledge");
        int knowledge = request.systemPrompt().indexOf("UNIVERSITY DOCUMENT RULES");
        int progress = request.systemPrompt().indexOf("ACADEMIC PROGRESS RULES");
        assertThat(knowledge).isPositive();
        assertThat(progress).isGreaterThan(knowledge);
    }

    @Test
    void courseReferencesStayAfterTheProgressTool() {
        var previous = new AcademicConversationReference("CS301", 15L, "get_my_courses", List.of(new PresentedCourseReference(1, "CS301", 15L)));
        var round = new LlmToolRound(new LlmResponse(null, "m", null, List.of(new LlmToolCall("c", TOOL, Map.of())), null),
                List.of(new LlmToolResult("c", TOOL, Map.of("success", true, "data", Map.of("cgpa", new BigDecimal("2.57"))))));
        var next = new AcademicReferenceResolverImpl().nextReference(previous, List.of(round), "Your cumulative GPA is 2.57.");
        assertThat(next.activeCourseCode()).isEqualTo("CS301");
        assertThat(next.activeCourseSectionId()).isEqualTo(15L);
        assertThat(next.lastPresentedCourses()).isEqualTo(previous.lastPresentedCourses());
        assertThat(next.lastAcademicIntent()).isEqualTo(TOOL);
    }
}
