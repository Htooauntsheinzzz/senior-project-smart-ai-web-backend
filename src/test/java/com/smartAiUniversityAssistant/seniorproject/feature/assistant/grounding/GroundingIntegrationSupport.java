package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.AssistantAcademicFixture;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Step 11 end-to-end: the real chat endpoint, JWT, tool executor, AcademicQueryService, PostgreSQL and Redis
 * (Testcontainers) with only Gemini scripted. Scripts may play a faulty model (wrong values, stale or another student's
 * facts) to prove the backend never returns such an answer. Uses AssistantAcademicFixture (see its Javadoc for the data).
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
abstract class GroundingIntegrationSupport extends IntegrationSupport {
    static final String CHAT = "/api/v1/student/assistant/chat";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean LlmClient llm;
    AssistantAcademicFixture fixture;
    final Deque<Function<LlmRequest, LlmResponse>> script = new ArrayDeque<>();
    final List<LlmRequest> sent = new ArrayList<>();
    private final Map<String, String> tokens = new HashMap<>();

    @BeforeEach
    void setUpGrounding() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        fixture = new AssistantAcademicFixture(db, encoder);
        fixture.create();
        script.clear();
        sent.clear();
        tokens.clear();
        when(llm.chat(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            sent.add(request);
            var step = script.poll();
            assertThat(step).as("unexpected provider call").isNotNull();
            return step.apply(request);
        });
    }

    @AfterEach
    void cleanUpGrounding() { fixture.clean(); }

    void tool(String name, Object... args) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < args.length; i += 2) map.put((String) args[i], args[i + 1]);
        script.add(r -> new LlmResponse(null, "scripted-model", null, List.of(new LlmToolCall("call-" + name, name, map)), null));
    }

    void answer(String text) { script.add(r -> new LlmResponse(text, "scripted-model", null)); }

    void answer(Function<LlmRequest, String> text) { script.add(r -> new LlmResponse(text.apply(r), "scripted-model", null)); }

    /** The data of the latest tool result the model received. */
    static Object lastData(LlmRequest r) {
        return ((Map<?, ?>) r.toolRounds().getLast().results().getLast().result()).get("data");
    }

    JsonNode chat(String student, String conversationId, String message) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        if (conversationId != null) body.put("conversationId", conversationId);
        var response = mvc.perform(post(CHAT).header("Authorization", "Bearer " + token(student)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsByteArray());
    }

    String ask(String student, String message) throws Exception {
        return chat(student, null, message).get("message").stringValue();
    }

    private String token(String student) throws Exception {
        String cached = tokens.get(student);
        if (cached != null) return cached;
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("universityEmail", student + "@rsu.ac.th", "password",
                        AssistantAcademicFixture.PASSWORD)))).andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(200);
        String token = json.readTree(result.getContentAsByteArray()).get("accessToken").asText();
        tokens.put(student, token);
        return token;
    }
}
