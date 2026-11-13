package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicProgressQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.impl.AcademicProgressQueryServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.AcademicProgressService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Spec file 56, section 17.7: the progress tool through the real endpoint, AcademicProgressService and PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(AssistantAcademicProgressIntegrationTests.Wiring.class)
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssistantAcademicProgressIntegrationTests extends IntegrationSupport {
    static final String CHAT = "/api/v1/student/assistant/chat";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired AcademicProgressService progressService;
    @Autowired FailingProgressQuery outage;
    @MockitoBean LlmClient llm;
    AssistantAcademicFixture fixture;
    AcademicProgressFixture progress;
    final Deque<LlmResponse> script = new ArrayDeque<>();
    final List<LlmRequest> sent = new ArrayList<>();
    final Map<String, String> tokens = new HashMap<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Wiring {
        @Bean @Primary AssistantProgressSettings progressSettings() { return AssistantProgressSettings.of(true); }
        @Bean @Primary FailingProgressQuery failingProgressQuery(AcademicProgressQueryServiceImpl delegate) { return new FailingProgressQuery(delegate); }
    }

    /** The real progress query, except when a test switches on a simulated database outage. */
    static final class FailingProgressQuery implements AcademicProgressQueryService {
        private final AcademicProgressQueryService delegate;
        volatile boolean failing;
        FailingProgressQuery(AcademicProgressQueryService delegate) { this.delegate = delegate; }
        @Override public AcademicProgressQueryResult getMyAcademicProgress() {
            if (failing) throw new DataAccessResourceFailureException("simulated database outage");
            return delegate.getMyAcademicProgress();
        }
    }

    @BeforeAll
    void createData() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        progress = new AcademicProgressFixture(db);
        progress.clean();
        fixture = new AssistantAcademicFixture(db, encoder);
        fixture.create();
        progress.create(fixture, progressService);
    }

    @AfterAll
    void cleanData() {
        progress.clean();
        fixture.clean();
    }

    @BeforeEach
    void model() {
        script.clear();
        sent.clear();
        outage.failing = false;
        when(llm.chat(any())).thenAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return script.poll();
        });
    }

    void progressCall() {
        script.add(new LlmResponse(null, "scripted-model", null, List.of(new LlmToolCall("p", "get_my_academic_progress", Map.of())), null));
    }

    void answer(String text) { script.add(new LlmResponse(text, "scripted-model", null)); }

    JsonNode chat(String student, String message) throws Exception {
        var response = mvc.perform(post(CHAT).header("Authorization", "Bearer " + token(student)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("message", message)))).andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    String token(String student) throws Exception {
        if (tokens.containsKey(student)) return tokens.get(student);
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                Map.of("universityEmail", student + "@rsu.ac.th", "password", AssistantAcademicFixture.PASSWORD)))).andReturn().getResponse();
        String token = json.readTree(result.getContentAsByteArray()).get("accessToken").asText();
        tokens.put(student, token);
        return token;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> toolData() {
        return (Map<String, Object>) ((Map<String, Object>) sent.getLast().toolRounds().getLast().results().getLast().result()).get("data");
    }

    @Test
    void studentAReceivesTheFixtureValues(CapturedOutput output) throws Exception {
        progressCall();
        answer("Your cumulative GPA is 2.57 and you have earned 18 credits.");
        var body = chat("STD-TEST-001", "What is my GPA?");
        assertThat(body.get("message").asString()).isEqualTo("Your cumulative GPA is 2.57 and you have earned 18 credits.");
        var data = toolData();
        assertThat(new java.math.BigDecimal(data.get("cgpa").toString())).isEqualByComparingTo("2.57");
        assertThat(new java.math.BigDecimal(data.get("creditsEarned").toString())).isEqualByComparingTo("18");
        assertThat(data).containsEntry("currentCourses", 3).containsEntry("currentEnrolledCredits", 9).containsEntry("studyYear", 3)
                .containsEntry("academicYear", 2026).containsEntry("dataStatus", "AVAILABLE").hasSize(7);
        assertThat(data.toString()).doesNotContain("STD-TEST").doesNotContain("Test Student").doesNotContain("PGR").doesNotContain("CS301");
        String log = output.getOut().substring(output.getOut().indexOf("requestId=" + body.get("requestId").asString()));
        assertThat(log).contains("tool=get_my_academic_progress success=true").doesNotContain("2.57").doesNotContain("=18 ");
    }

    @Test
    void studentBHasNoResults() throws Exception {
        progressCall();
        answer("Your GPA is 3.20.");
        var body = chat("STD-TEST-002", "What is my GPA?");
        assertThat(toolData()).containsEntry("cgpa", null).containsEntry("creditsEarned", null).containsEntry("dataStatus", "PARTIAL");
        assertThat(body.get("message").asString()).startsWith("Your academic progress:").contains("Cumulative GPA: not currently available");
    }

    @Test
    void databaseFailureAndAccess() throws Exception {
        outage.failing = true;
        progressCall();
        var body = chat("STD-TEST-001", "What is my GPA?");
        assertThat(body.get("message").asString()).isEqualTo("I can't verify your academic information right now, so I won't guess. "
                + "Please try again in a few minutes.");
        assertThat(sent).hasSize(1);
        mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"What is my GPA?\"}"))
                .andExpect(status().isUnauthorized());
    }
}
