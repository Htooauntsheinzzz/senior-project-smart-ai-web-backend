package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntentCatalog;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.StudentAssistantServiceImpl;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Step 8 regression dataset (src/test/resources/assistant/step8-intent-regression.json): 50+ positive questions over
 * the ten intents and 20+ negative/edge cases, run through the real chat endpoint, security chain, tool executor,
 * AcademicQueryService and PostgreSQL. Only the model is scripted: each case states which tool calls Gemini is
 * expected to make, so this verifies everything after tool selection (arguments, Student scoping, facts read from
 * the database, error codes, grounding). Whether Gemini itself picks those tools is checked live (spec §26).
 * A per-case pass/fail report is written to target/step8-intent-regression-report.json.
 * <p>
 * Step 11: a not-found, ambiguous or empty result is answered by the backend without another provider call, so
 * expectedReply BACKEND checks the deterministic reply (replyContains) and tool error codes are read from the
 * ASSISTANT_TOOL log lines of the request, which also cover a round the model never saw.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AcademicIntentRegressionTests extends IntegrationSupport {
    private static final String CHAT = "/api/v1/student/assistant/chat";
    private static final String PASSWORD = AssistantAcademicFixture.PASSWORD;
    private static final Map<String, Boolean> REPORT = Collections.synchronizedMap(new TreeMap<>());
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean LlmClient llm;
    private AssistantAcademicFixture fixture;
    private long studentA;
    private final Map<String, Object> placeholders = new HashMap<>();

    static List<Map<String, Object>> cases(String kind) { return IntentRegressionDataset.cases(kind); }
    static Stream<Named<Map<String, Object>>> positiveCases() { return named(cases("positive")); }
    static Stream<Named<Map<String, Object>>> negativeCases() { return named(cases("negative")); }
    private static Stream<Named<Map<String, Object>>> named(List<Map<String, Object>> cases) {
        return cases.stream().map(c -> Named.of(c.get("caseId") + " " + c.get("utterance"), c));
    }

    @BeforeEach
    void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        fixture = new AssistantAcademicFixture(db, encoder);
        fixture.create();
        studentA = fixture.studentA();
        placeholders.putAll(fixture.placeholders());
    }

    @AfterEach
    void cleanup() { fixture.clean(); }

    @AfterAll
    static void writeReport() throws Exception {
        long passed = REPORT.values().stream().filter(Boolean::booleanValue).count();
        var report = new LinkedHashMap<String, Object>();
        report.put("executed", REPORT.size());
        report.put("passed", passed);
        report.put("cases", REPORT);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "step8-intent-regression-report.json"),
                JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positiveCases")
    @SuppressWarnings("unchecked")
    void positiveIntentCase(Map<String, Object> testCase) throws Exception {
        record(testCase, () -> {
            var rounds = (List<List<Map<String, Object>>>) testCase.get("rounds");
            var steps = new ArrayList<Map<String, Object>>();
            for (var round : rounds) steps.add(Map.of("tools", round));
            String answer = "Answer for " + testCase.get("caseId");
            steps.add(Map.of("text", answer));
            script(steps);
            long enrollments = enrollmentCount();

            var result = chat(login("STD-TEST-001"), text(testCase.get("utterance"))).andReturn().getResponse();
            assertThat(result.getStatus()).isEqualTo(200);
            assertThat(json.readTree(result.getContentAsByteArray()).get("message").asText()).isEqualTo(answer);

            List<LlmRequest> sent = sentRequests(rounds.size() + 1);
            LlmRequest last = sent.getLast();
            assertThat(sent.getFirst().systemPrompt()).contains(AcademicIntentCatalog.promptSection());
            assertThat(sent).noneMatch(LlmRequest::requireTool);
            List<String> executed = last.toolRounds().stream().flatMap(r -> r.results().stream()).map(LlmToolResult::toolName).toList();
            assertThat(executed).isEqualTo(testCase.get("expectedTools"));
            for (var round : last.toolRounds())
                for (var toolResult : round.results())
                    assertThat((Map<String, Object>) toolResult.result()).containsEntry("success", true);
            assertFacts((Map<String, Object>) testCase.get("expectedCoreFacts"), json.writeValueAsString(last.toolRounds()));
            assertThat(enrollmentCount()).isEqualTo(enrollments);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("negativeCases")
    @SuppressWarnings("unchecked")
    void negativeAndEdgeCase(Map<String, Object> testCase, CapturedOutput output) throws Exception {
        record(testCase, () -> {
            var steps = (List<Map<String, Object>>) testCase.get("script");
            script(steps);
            long enrollments = enrollmentCount();
            String token = token(testCase);

            var request = post(CHAT).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("message", text(testCase.get("utterance")))));
            if (token != null) request = request.header("Authorization", "Bearer " + token);
            var response = mvc.perform(request).andReturn().getResponse();
            String body = response.getContentAsString();

            Object expectedStatus = testCase.getOrDefault("expectedStatus", 200);
            if (expectedStatus instanceof List<?> allowed) assertThat(allowed.contains(response.getStatus())).as("status " + response.getStatus()).isTrue();
            else assertThat(response.getStatus()).isEqualTo(expectedStatus);
            if (testCase.get("errorCode") != null)
                assertThat(json.readTree(body).get("code").asText()).isEqualTo(testCase.get("errorCode"));
            assertThat(enrollmentCount()).isEqualTo(enrollments);
            String message = response.getStatus() == 200 ? json.readTree(body).get("message").asText() : null;
            for (Object expected : (List<Object>) testCase.getOrDefault("replyContains", List.of()))
                assertThat(message).contains((String) expected);
            if (steps.isEmpty()) {
                verifyNoInteractions(llm);
                return;
            }

            var captor = ArgumentCaptor.forClass(LlmRequest.class);
            verify(llm, atLeastOnce()).chat(captor.capture());
            List<LlmRequest> sent = captor.getAllValues();
            if (testCase.get("providerRequests") != null) assertThat(sent).hasSize((Integer) testCase.get("providerRequests"));
            if (Boolean.TRUE.equals(testCase.get("groundingRetry"))) {
                assertThat(sent.get(0).requireTool()).isFalse();
                assertThat(sent.get(1).requireTool()).isTrue();
            } else if (Boolean.FALSE.equals(testCase.get("groundingRetry"))) {
                assertThat(sent).noneMatch(LlmRequest::requireTool);
            }
            String toolResults = json.writeValueAsString(sent.getLast().toolRounds());
            if (testCase.get("toolCodes") != null)
                assertThat(failedToolCodes(output, json.readTree(body).get("requestId").asText())).isEqualTo(testCase.get("toolCodes"));
            if (response.getStatus() == 200) {
                if ("FALLBACK".equals(testCase.get("expectedReply")))
                    assertThat(message).isEqualTo(StudentAssistantServiceImpl.UNGROUNDED_FALLBACK_MESSAGE);
                else if (!"BACKEND".equals(testCase.get("expectedReply")))
                    assertThat(message).isEqualTo(steps.getLast().get("text"));
            }
            assertFacts(testCase, toolResults + body);
            for (Object hidden : (List<Object>) testCase.getOrDefault("bodyNotContains", List.of()))
                assertThat(body).doesNotContain((String) hidden);
        });
    }

    private interface Check { void run() throws Exception; }

    // Error codes of this request's failed tool calls, in execution order, from the ASSISTANT_TOOL log lines.
    private static List<String> failedToolCodes(CapturedOutput output, String requestId) {
        Matcher m = Pattern.compile("ASSISTANT_TOOL requestId=" + Pattern.quote(requestId) + " .*? success=false code=(\\S+)")
                .matcher(output.getOut());
        var codes = new ArrayList<String>();
        while (m.find()) codes.add(m.group(1));
        return codes;
    }

    // Pass/fail per case for the report; the assertion error still fails the test.
    private static void record(Map<String, Object> testCase, Check check) throws Exception {
        String caseId = (String) testCase.get("caseId");
        REPORT.put(caseId, false);
        check.run();
        REPORT.put(caseId, true);
    }

    @SuppressWarnings("unchecked")
    private static void assertFacts(Map<String, Object> facts, String text) {
        for (Object expected : (List<Object>) facts.getOrDefault("contains", List.of())) assertThat(text).contains((String) expected);
        for (Object hidden : (List<Object>) facts.getOrDefault("notContains", List.of())) assertThat(text).doesNotContain((String) hidden);
    }

    /** Scripted model by provider call: a text step answers, a tools step requests those calls (placeholders resolved). */
    @SuppressWarnings("unchecked")
    private void script(List<Map<String, Object>> steps) {
        var calls = new AtomicInteger();
        when(llm.chat(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            int index = Math.min(calls.getAndIncrement(), steps.size() - 1);
            Map<String, Object> step = steps.get(index);
            if (step.containsKey("text")) return new LlmResponse((String) step.get("text"), "m", null);
            var toolCalls = new ArrayList<LlmToolCall>();
            for (var call : (List<Map<String, Object>>) step.get("tools")) {
                var args = new LinkedHashMap<String, Object>();
                ((Map<String, Object>) call.get("arguments")).forEach((key, value) -> args.put(key, value(value, request)));
                toolCalls.add(new LlmToolCall("call-" + index + "-" + toolCalls.size(), (String) call.get("name"), args));
            }
            return new LlmResponse(null, "m", null, toolCalls, null);
        });
    }

    @SuppressWarnings("unchecked")
    private Object value(Object value, LlmRequest request) {
        if (!(value instanceof String s) || !s.startsWith("${")) return value;
        String name = s.substring(2, s.length() - 1);
        if (name.equals("FROM_PREVIOUS")) {
            var previous = (Map<String, Object>) request.toolRounds().getLast().results().getFirst().result();
            return ((Map<String, Object>) previous.get("data")).get("courseSectionId");
        }
        return Objects.requireNonNull(placeholders.get(name), name);
    }

    private String text(Object utterance) {
        String text = (String) utterance;
        for (var entry : placeholders.entrySet()) text = text.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        return text;
    }

    private String token(Map<String, Object> testCase) throws Exception {
        String student = switch ((String) testCase.getOrDefault("student", "A")) {
            case "B" -> "STD-TEST-002";
            case "C" -> "STD-TEST-003";
            default -> "STD-TEST-001";
        };
        return switch ((String) testCase.getOrDefault("auth", "STUDENT")) {
            case "NONE" -> null;
            case "INVALID" -> "not-a-jwt";
            case "SUSPENDED" -> {
                String token = login(student);
                db.update("UPDATE students SET account_status='SUSPENDED' WHERE id=?", studentA);
                yield token;
            }
            case "FORCE_PASSWORD_CHANGE" -> {
                db.update("UPDATE student_credentials SET force_password_change=TRUE WHERE student_id=?", studentA);
                yield login(student);
            }
            default -> login(student);
        };
    }

    private List<LlmRequest> sentRequests(int expected) {
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(expected)).chat(captor.capture());
        return captor.getAllValues();
    }
    private long enrollmentCount() { return db.queryForObject("SELECT count(*) FROM enrollments", Long.class); }
    private ResultActions chat(String token, String message) throws Exception {
        MockHttpServletRequestBuilder request = post(CHAT).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("message", message)));
        return mvc.perform(request);
    }
    private String login(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
}
