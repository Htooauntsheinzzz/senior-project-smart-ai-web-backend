package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.AssistantAcademicFixture;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.EvaluationTrace.ObservedToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolExecutorImpl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared plumbing of both Step 9 modes: the fixture, Student login, and one case sent through the real chat endpoint
 * (JWT, security chain, StudentAssistantService, tool executor, AcademicQueryService, PostgreSQL). The model sits
 * behind {@link RecordingLlmClient} and the tool executor behind {@link RecordingAcademicAiToolExecutor}; the trace
 * is every executed tool call with its arguments and the backend's result (including a last round that the backend
 * answered itself, Step 11), numbered by the provider turn that requested it. Nothing is added to the production
 * response.
 */
@Import(IntentEvaluationSupport.RecorderConfiguration.class)
abstract class IntentEvaluationSupport extends IntegrationSupport {
    static final String CHAT = "/api/v1/student/assistant/chat";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder encoder;
    @Autowired RecordingLlmClient recorder;
    @Autowired RecordingAcademicAiToolExecutor toolRecorder;
    @Autowired MutableClock clock;
    AssistantAcademicFixture fixture;
    private final Map<String, String> tokens = new HashMap<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class RecorderConfiguration {
        @Bean @Primary RecordingLlmClient recordingLlmClient() { return new RecordingLlmClient(); }
        @Bean @Primary RecordingAcademicAiToolExecutor recordingToolExecutor(AcademicAiToolExecutorImpl executor) {
            return new RecordingAcademicAiToolExecutor(executor);
        }
    }

    void createFixture() {
        clock.set(java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        fixture = new AssistantAcademicFixture(db, encoder);
        fixture.create();
        tokens.clear();
    }

    /** Sends the (resolved) case's input as the case's test student and observes what happened. */
    EvaluationTrace run(IntentEvaluationCase c) throws Exception {
        return send(c.testStudent(), c.input(), null).trace();
    }

    /** One chat turn and the conversationId to continue with (Step 11 multi-turn grounding cases). */
    record Turn(EvaluationTrace trace, String conversationId) {}

    /** Sends one message as the test student, optionally continuing a conversation, and observes what happened. */
    Turn send(String testStudent, String message, String conversationId) throws Exception {
        String token = token(testStudent);
        recorder.startCase();
        toolRecorder.startCase();
        String before = fixture.enrollmentFingerprint();
        long started = System.nanoTime();
        var request = new LinkedHashMap<String, Object>();
        request.put("message", message);
        if (conversationId != null) request.put("conversationId", conversationId);
        var response = mvc.perform(post(CHAT).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request))).andReturn().getResponse();
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        boolean modified = !before.equals(fixture.enrollmentFingerprint());
        JsonNode body = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        int status = response.getStatus();
        List<LlmRequest> sent = recorder.requests();
        // Round n is the n-th provider turn that asked for tools; a call the backend made itself is round 0.
        var rounds = new ArrayDeque<Map.Entry<String, Integer>>();
        int round = 0;
        for (LlmResponse turn : recorder.responses())
            if (turn != null && turn.hasToolCalls()) {
                round++;
                for (LlmToolCall call : turn.toolCalls()) rounds.add(Map.entry(String.valueOf(call.callId()), round));
            }
        var calls = new ArrayList<ObservedToolCall>();
        var results = new ArrayList<Object>();
        for (var execution : toolRecorder.executions()) {
            LlmToolCall call = execution.call();
            Map<String, Object> payload = execution.result().payload();
            var next = rounds.peek();
            int callRound = next != null && next.getKey().equals(String.valueOf(call.callId())) ? rounds.poll().getValue() : 0;
            results.add(payload);
            calls.add(new ObservedToolCall(callRound, call.name(), call.arguments(), Boolean.TRUE.equals(payload.get("success")),
                    (String) payload.get("code")));
        }
        return new Turn(new EvaluationTrace(text(body, "requestId"), status, status == 200 ? null : text(body, "code"),
                status == 200 ? text(body, "message") : null, text(body, "model"), calls, json.writeValueAsString(results),
                sent.size(), durationMs, modified), text(body, "conversationId"));
    }

    /** SHA-256 of the system prompt and tool declarations the model received, for reproducibility. */
    String promptFingerprint() throws Exception {
        List<LlmRequest> sent = recorder.requests();
        if (sent.isEmpty()) return null;
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(
                List.of(sent.getFirst().systemPrompt(), sent.getFirst().tools())));
        return HexFormat.of().formatHex(digest);
    }

    /** Forget cached tokens, e.g. after the fixture was re-created. */
    void clearTokens() { tokens.clear(); }

    private String token(String studentCode) throws Exception {
        String cached = tokens.get(studentCode);
        if (cached != null) return cached;
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("universityEmail", studentCode + "@rsu.ac.th",
                        "password", AssistantAcademicFixture.PASSWORD)))).andReturn().getResponse();
        if (result.getStatus() != 200) throw new IllegalStateException("login failed for " + studentCode + ": " + result.getStatus());
        String token = json.readTree(result.getContentAsByteArray()).get("accessToken").asText();
        tokens.put(studentCode, token);
        return token;
    }

    private static String text(JsonNode body, String field) {
        return body != null && body.hasNonNull(field) ? body.get(field).asText() : null;
    }
}
