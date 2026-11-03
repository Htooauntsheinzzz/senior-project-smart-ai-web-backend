package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Chat endpoint through the real security chain and database, with the LLM mocked (no key, no network). */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class StudentAssistantIntegrationTests extends IntegrationSupport {
    private static final String CHAT = "/api/v1/student/assistant/chat";
    private static final String ADMIN_PASSWORD = "assistant actor password";
    private static final String PASSWORD = "student assistant password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean LlmClient llm;
    private long studentA, studentB;

    @BeforeEach
    void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        cleanDatabase();
        db.update("UPDATE app_roles SET is_active=TRUE");
        long actor = id("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('AST-ACTOR','Assistant','Actor','assistant.actor@example.test','ACTIVE') RETURNING id
                """);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actor, encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='SUPER_ADMIN'", actor);
        String hash = encoder.encode(PASSWORD);
        studentA = student("6700001", hash);
        studentB = student("6700002", hash);
        when(llm.chat(any())).thenReturn(new LlmResponse("Hello. How can I help you?", "provider-model", "chatcmpl-1"));
    }

    @AfterEach
    void cleanup() { cleanDatabase(); }

    @Test
    void studentChatsAndGetsTheAssistantAnswer() throws Exception {
        String token = login("6700001");
        var result = chat(token, "{\"message\":\"  Hello, what can you help me with?  \"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("message").value("Hello. How can I help you?"))
                .andExpect(jsonPath("model").value("provider-model"))
                .andExpect(jsonPath("requestId").isString())
                .andExpect(jsonPath("createdAt").isString())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        // Only the five documented fields: no prompt, provider payload, key, conversation owner or student data.
        var names = new ArrayList<String>();
        body.propertyNames().forEach(names::add);
        assertThat(names).containsExactlyInAnyOrder("message", "requestId", "conversationId", "model", "createdAt");
        String raw = result.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("chatcmpl-1").doesNotContain("systemPrompt").doesNotContain("studentId");

        var sent = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).chat(sent.capture());
        assertThat(sent.getValue().userMessage()).isEqualTo("Hello, what can you help me with?");
        assertThat(sent.getValue().requestId()).isEqualTo(body.get("requestId").stringValue());
        // The student's JWT is never part of what goes to the provider.
        assertThat(sent.getValue().toString()).doesNotContain(token).doesNotContain("Bearer");
    }

    @Test
    void studentIdentityComesOnlyFromTheToken() throws Exception {
        String token = login("6700001");
        // A body studentId is not accepted (unknown JSON properties are rejected project-wide).
        chat(token, "{\"studentId\":" + studentB + ",\"message\":\"Hello\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        // A query parameter or custom header is simply ignored; the call still runs as the token's student.
        mvc.perform(post(CHAT + "?studentId=" + studentB).header("Authorization", "Bearer " + token)
                        .header("X-Student-Id", String.valueOf(studentB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk());
        // Generation settings cannot be controlled by the client either.
        for (String field : List.of("\"model\":\"x\"", "\"systemPrompt\":\"x\"", "\"temperature\":1", "\"apiKey\":\"x\"", "\"provider\":\"x\""))
            chat(token, "{\"message\":\"Hello\"," + field + "}").andExpect(status().isBadRequest());
        verify(llm, times(1)).chat(any());
    }

    @Test
    void invalidMessagesAreRejectedWithoutCallingTheProvider() throws Exception {
        String token = login("6700001");
        for (String body : List.of("{\"message\":\"\"}", "{\"message\":\"     \"}", "{\"message\":null}", "{}",
                "{\"message\":\"" + "x".repeat(4001) + "\"}", "{\"message\":[\"a\"]}", "not json", "")) {
            chat(token, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("path").value(CHAT));
        }
        chat(token, "{\"message\":\"" + "x".repeat(4000) + "\"}").andExpect(status().isOk());
        verify(llm, times(1)).chat(any());
    }

    @Test
    void multilingualMessagesRoundTripAsUtf8() throws Exception {
        when(llm.chat(any())).thenReturn(new LlmResponse("หน่วยกิตคือ… / ခရက်ဒစ်ဆိုတာ…", "m", null));
        String token = login("6700001");
        mvc.perform(post(CHAT).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("message", "หน่วยกิตคืออะไร ခရက်ဒစ်ဆိုတာဘာလဲ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("message").value("หน่วยกิตคือ… / ခရက်ဒစ်ဆိုတာ…"));
        var sent = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).chat(sent.capture());
        assertThat(sent.getValue().userMessage()).isEqualTo("หน่วยกิตคืออะไร ခရက်ဒစ်ဆိုတာဘာလဲ");
    }

    @Test
    void routeRequiresAFullAccessStudentToken() throws Exception {
        mvc.perform(post(CHAT).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
        chat("not-a-jwt", "{\"message\":\"Hello\"}").andExpect(status().isUnauthorized());

        // Admin tokens never gain Student assistant access.
        chat(adminLogin(), "{\"message\":\"Hello\"}").andExpect(status().isForbidden());

        // Expired token (15 minute TTL plus 30 second skew).
        String expiring = login("6700002");
        Instant issued = clock.instant();
        clock.set(issued.plus(Duration.ofMinutes(16)));
        chat(expiring, "{\"message\":\"Hello\"}").andExpect(status().isUnauthorized());
        clock.set(issued);

        // Forced password change follows the existing /api/v1/student/** policy.
        db.update("UPDATE student_credentials SET force_password_change=TRUE WHERE student_id=?", studentA);
        chat(login("6700001"), "{\"message\":\"Hello\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        verifyNoInteractions(llm);
    }

    @Test
    void nonActiveOrDeletedStudentsAreRejected() throws Exception {
        for (String state : List.of("PENDING", "INACTIVE", "SUSPENDED")) {
            db.update("UPDATE students SET account_status='ACTIVE' WHERE id=?", studentA);
            String token = login("6700001");
            chat(token, "{\"message\":\"Hello\"}").andExpect(status().isOk());
            db.update("UPDATE students SET account_status=? WHERE id=?", state, studentA);
            // The session's cached auth state still passes; the service's eligibility query rejects the account.
            chat(token, "{\"message\":\"Hello\"}").andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("STUDENT_ACCOUNT_NOT_ACTIVE"));
        }
        db.update("UPDATE students SET account_status='ACTIVE' WHERE id=?", studentA);
        String token = login("6700001");
        db.update("UPDATE students SET is_deleted=TRUE WHERE id=?", studentA);
        // A deleted account fails the per-request session check before the service runs.
        chat(token, "{\"message\":\"Hello\"}").andExpect(status().isUnauthorized());
        verify(llm, times(3)).chat(any());
    }

    @Test
    void providerFailuresMapToSafeApplicationErrors() throws Exception {
        String token = login("6700001");
        record Case(AiAssistantFailure failure, int status, String code) {}
        for (Case c : List.of(new Case(AiAssistantFailure.unavailable(), 503, "AI_ASSISTANT_UNAVAILABLE"),
                new Case(AiAssistantFailure.timeout(), 503, "AI_REQUEST_TIMEOUT"),
                new Case(AiAssistantFailure.rateLimited(), 429, "AI_PROVIDER_RATE_LIMITED"),
                new Case(AiAssistantFailure.invalidProviderResponse(), 502, "INVALID_AI_PROVIDER_RESPONSE"))) {
            reset(llm);
            when(llm.chat(any())).thenThrow(c.failure());
            String body = chat(token, "{\"message\":\"Hello\"}")
                    .andExpect(status().is(c.status()))
                    .andExpect(jsonPath("code").value(c.code()))
                    .andExpect(jsonPath("path").value(CHAT))
                    .andExpect(jsonPath("traceId").isString())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContainIgnoringCase("api key").doesNotContain("Exception").doesNotContain("provider");
        }
        // An unexpected programming error still returns only the generic shape.
        reset(llm);
        when(llm.chat(any())).thenThrow(new IllegalStateException("secret detail https://provider.example/v1?key=abc"));
        String body = chat(token, "{\"message\":\"Hello\"}").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("code").value("INTERNAL_ERROR")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("secret detail").doesNotContain("key=abc");
    }

    private ResultActions chat(String token, String body) throws Exception {
        return mvc.perform(post(CHAT).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private void cleanDatabase() {
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
        db.update("DELETE FROM app_user_roles WHERE user_id IN (SELECT id FROM app_users WHERE employee_id='AST-ACTOR')");
        db.update("DELETE FROM appuser_credentials WHERE user_id IN (SELECT id FROM app_users WHERE employee_id='AST-ACTOR')");
        db.update("DELETE FROM app_users WHERE employee_id='AST-ACTOR'");
    }
    private long student(String code, String hash) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status)
                VALUES (?,?,'Test','Student','ACTIVE') RETURNING id
                """, code, code + "@rsu.ac.th");
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, hash);
        return id;
    }
    private String login(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    private String adminLogin() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", "assistant.actor@example.test", "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
