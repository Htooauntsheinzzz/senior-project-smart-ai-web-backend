package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.PublishedMeetingTestData;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Step 10 multi-turn conversations through the real endpoint, JWT, Redis (Testcontainers), tool executor,
 * AcademicQueryService and PostgreSQL. Only the model is scripted; where the real model would use the conversation
 * context, the script reads the referent the backend put into the system prompt, so these tests check that the backend
 * resolves the right course, re-queries PostgreSQL for it and stores the conversation safely.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AssistantConversationIntegrationTests extends IntegrationSupport {
    private static final String CHAT = "/api/v1/student/assistant/chat";
    private static final String KEY_PREFIX = "student:assistant:conversation:";
    private static final Pattern REFERENT = Pattern.compile("(?:which is|refers to) ([A-Z]{2,5}\\d{3,4})\\.");
    private static final Pattern UUID_V4 = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean LlmClient llm;
    private AssistantAcademicFixture fixture;
    private final Deque<Function<LlmRequest, LlmResponse>> script = new ArrayDeque<>();
    private final List<LlmRequest> sent = new ArrayList<>();
    private String tokenA;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        fixture = new AssistantAcademicFixture(db, encoder);
        fixture.create();
        script.clear();
        sent.clear();
        when(llm.chat(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            sent.add(request);
            var step = script.poll();
            assertThat(step).as("unexpected provider call").isNotNull();
            return step.apply(request);
        });
        tokenA = login("STD-TEST-001");
    }

    @AfterEach
    void cleanup() { fixture.clean(); }

    // ---- Spec §51: creation ----------------------------------------------------------------------------------

    @Test
    void firstMessageCreatesAnOwnedConversationInRedis() throws Exception {
        listCourses();
        JsonNode body = ok(chat(tokenA, null, "What courses do I have?"));

        String id = body.get("conversationId").stringValue();
        assertThat(id).matches(UUID_V4);
        assertThat(body.get("message").stringValue()).isEqualTo("1. CS301 — Data Structures\n2. CSC460 — Cloud Computing\n3. CSC452 — Mobile Programming");
        String key = KEY_PREFIX + id;
        assertThat(redis.keys(KEY_PREFIX + "*")).containsExactly(key);
        long ttl = redis.getExpire(key);
        assertThat(ttl).isBetween(1700L, 1800L);

        JsonNode stored = json.readTree(redis.opsForValue().get(key));
        assertThat(stored.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(stored.get("conversationId").stringValue()).isEqualTo(id);
        assertThat(stored.get("studentId").asLong()).isEqualTo(fixture.studentA());
        assertThat(stored.get("version").asLong()).isEqualTo(1);
        assertThat(stored.get("messages")).hasSize(2);
        assertThat(stored.get("messages").get(0).get("role").stringValue()).isEqualTo("USER");
        assertThat(stored.get("messages").get(0).get("content").stringValue()).isEqualTo("What courses do I have?");
        assertThat(stored.get("messages").get(1).get("role").stringValue()).isEqualTo("ASSISTANT");
        // The order the student saw, not the database order (CS301, CSC452, CSC460).
        assertThat(presented(stored)).containsExactly("CS301", "CSC460", "CSC452");
        assertThat(stored.get("academicReference").get("lastAcademicIntent").stringValue()).isEqualTo(GET_MY_COURSES);
        assertThat(stored.get("academicReference").get("activeCourseCode").isNull()).isTrue();
        // The first request of a conversation carries no history and no conversation section.
        assertThat(sent.getFirst().history()).isEmpty();
        assertThat(sent.getFirst().systemPrompt()).doesNotContain("Conversation context");
    }

    @Test
    void aMessageWithoutConversationIdAlwaysStartsAFreshConversation() throws Exception {
        listCourses();
        String first = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();
        script.add(r -> text("You can ask me about your courses."));
        String second = ok(chat(tokenA, null, "What can you do?")).get("conversationId").stringValue();

        assertThat(second).isNotEqualTo(first);
        assertThat(sent.getLast().history()).isEmpty();
        assertThat(sent.getLast().systemPrompt()).doesNotContain("Conversation context").doesNotContain("CSC460");
        assertThat(redis.keys(KEY_PREFIX + "*")).hasSize(2);
    }

    // ---- Spec §52/§53: follow-ups ----------------------------------------------------------------------------

    @Test
    void firstOneAndItResolveToTheSameCourseAndAreReQueried() throws Exception {
        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();

        askAboutReferent(GET_MY_COURSE_LECTURER, "lecturerNameEn");
        JsonNode second = ok(chat(tokenA, id, "Who teaches the first one?"));
        assertThat(second.get("conversationId").stringValue()).isEqualTo(id);
        assertThat(second.get("message").stringValue()).isEqualTo("CS301: Dr. John Smith");
        LlmRequest followUp = sent.get(sent.size() - 2);
        assertThat(followUp.systemPrompt()).contains("1. CS301 (courseSectionId " + fixture.placeholders().get("CS301_SEC01") + ")")
                .contains("item 1 of that list, which is CS301.");
        // History: the earlier pair as ordinary user/assistant messages, the new question separately.
        assertThat(followUp.history()).extracting(ConversationMessage::role).containsExactly("USER", "ASSISTANT");
        assertThat(followUp.history().getFirst().content()).isEqualTo("What courses do I have?");
        assertThat(followUp.userMessage()).isEqualTo("Who teaches the first one?");
        assertThat(toolArguments(sent.getLast())).containsExactly(Map.entry("courseIdentifier", "CS301"));

        askAboutReferent(GET_MY_COURSE_SECTION, "room");
        JsonNode third = ok(chat(tokenA, id, "Where is it?"));
        assertThat(third.get("message").stringValue()).isEqualTo("CS301: 11-402");
        assertThat(sent.get(sent.size() - 2).systemPrompt()).contains("Course currently being discussed: CS301.")
                .contains("refers to CS301.");
        assertThat(toolArguments(sent.getLast())).containsExactly(Map.entry("courseIdentifier", "CS301"));
        assertThat(sent.get(sent.size() - 2).history()).hasSize(4);

        JsonNode stored = stored(id);
        assertThat(stored.get("version").asLong()).isEqualTo(3);
        assertThat(stored.get("messages")).hasSize(6);
        assertThat(stored.get("academicReference").get("activeCourseCode").stringValue()).isEqualTo("CS301");
        assertThat(stored.get("academicReference").get("lastAcademicIntent").stringValue()).isEqualTo(GET_MY_COURSE_SECTION);
        assertThat(presented(stored)).containsExactly("CS301", "CSC460", "CSC452");
    }

    @Test
    void contextSwitchesToTheSecondCourseAndAnExplicitCourseOverridesIt() throws Exception {
        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();

        askAboutReferent(GET_MY_COURSE_LECTURER, "lecturerNameEn");
        assertThat(ok(chat(tokenA, id, "Who teaches the second one?")).get("message").stringValue())
                .isEqualTo("CSC460: Dr. Jane Lee");

        // CSC460 has no published timetable: the empty list comes from PostgreSQL and is passed on, never invented.
        askAboutReferent(GET_MY_COURSE_SECTION, "room");
        assertThat(ok(chat(tokenA, id, "Where is it?")).get("message").stringValue()).isEqualTo("CSC460: not available");

        script.add(r -> {
            assertThat(r.systemPrompt()).contains("names a course itself").doesNotContain("refers to CSC460.");
            return tool(GET_MY_COURSE_LECTURER, Map.of("courseIdentifier", "CSC452"));
        });
        script.add(r -> text(answer(r, "lecturerNameEn")));
        assertThat(ok(chat(tokenA, id, "Now who teaches CSC452?")).get("message").stringValue()).isEqualTo("CSC452: Dr. Jane Lee");
        assertThat(stored(id).get("academicReference").get("activeCourseCode").stringValue()).isEqualTo("CSC452");

        askAboutReferent(GET_MY_COURSE_SECTION, "room");
        assertThat(ok(chat(tokenA, id, "Where is that course?")).get("message").stringValue()).isEqualTo("CSC452: 5-201");
    }

    // ---- Spec §56/§37: Redis never replaces PostgreSQL ---------------------------------------------------------

    @Test
    void followUpReadsTheCurrentRoomNotTheRememberedOne() throws Exception {
        script.add(r -> tool(GET_MY_COURSE_SECTION, Map.of("courseIdentifier", "CS301")));
        script.add(r -> text(answer(r, "room")));
        String id = ok(chat(tokenA, null, "Where is my CS301 class?")).get("conversationId").stringValue();
        assertThat(stored(id).get("messages").get(1).get("content").stringValue()).isEqualTo("CS301: 11-402");

        PublishedMeetingTestData.setRoom(db, (Long) fixture.placeholders().get("CS301_SEC01"), "11-405");
        askAboutReferent(GET_MY_COURSE_SECTION, "room");
        assertThat(ok(chat(tokenA, id, "Where is it now?")).get("message").stringValue()).isEqualTo("CS301: 11-405");
        // The stored value only ever points at CS301; the room came from a new tool run.
        assertThat(stored(id).get("academicReference").toString()).doesNotContain("11-40");
    }

    @Test
    void aRememberedCourseIsNotProofOfCurrentEnrollment() throws Exception {
        script.add(r -> tool(GET_MY_COURSE_LECTURER, Map.of("courseIdentifier", "CS301")));
        script.add(r -> text(answer(r, "lecturerNameEn")));
        String id = ok(chat(tokenA, null, "Who teaches my CS301 class?")).get("conversationId").stringValue();

        db.update("UPDATE enrollments SET status='DROPPED' WHERE student_id=? AND course_section_id=?",
                fixture.studentA(), fixture.placeholders().get("CS301_SEC01"));
        askAboutReferent(GET_MY_COURSE_SECTION, "room");
        String reply = ok(chat(tokenA, id, "Where is it?")).get("message").stringValue();

        // Step 11: the not-enrolled result is answered by the backend itself, not handed back to the model.
        assertThat(reply).isEqualTo("No matching enrollment was found for your account for CS301: you are not currently "
                + "enrolled in that course.").doesNotContain("11-402");
        assertThat(script).hasSize(1);
        assertThat(stored(id).get("messages").get(3).get("content").stringValue()).isEqualTo(reply);
    }

    // ---- Spec §57: ambiguity ---------------------------------------------------------------------------------

    @Test
    void unclearReferencesAreSentToTheModelAsQuestionsToAskNotGuesses() throws Exception {
        script.add(r -> text("You can ask me about your courses."));
        String id = ok(chat(tokenA, null, "What can you help with?")).get("conversationId").stringValue();

        script.add(r -> {
            assertThat(r.systemPrompt()).contains("No numbered course list has been shown")
                    .contains("no single course is being discussed. Do not guess");
            assertThat(r.requireTool()).isFalse();
            return text("Which course are you asking about?");
        });
        assertThat(ok(chat(tokenA, id, "Where is it?")).get("message").stringValue()).isEqualTo("Which course are you asking about?");

        script.add(r -> {
            assertThat(r.systemPrompt()).contains("a position in a list, but no course list has been shown");
            return text("Which course do you mean?");
        });
        ok(chat(tokenA, id, "Who teaches the first one?"));

        listCourses();
        ok(chat(tokenA, id, "What courses do I have?"));
        script.add(r -> {
            assertThat(r.systemPrompt()).contains("refers to item 5, but the list shown has only 3 courses");
            return text("You have only three courses. Which one do you mean?");
        });
        ok(chat(tokenA, id, "Who teaches the fifth one?"));
        // A list of three and no single course discussed: "it" stays ambiguous.
        script.add(r -> {
            assertThat(r.systemPrompt()).contains("no single course is being discussed");
            return text("Which course do you mean?");
        });
        ok(chat(tokenA, id, "Who teaches it?"));
    }

    // ---- Spec §54/§55: expiry, ids and isolation ---------------------------------------------------------------

    @Test
    void invalidMissingAndExpiredConversationsFailBeforeAnyProviderCall() throws Exception {
        for (String bad : List.of("not-a-uuid", "", "9FA36C31-F92B-4D1E-9F4E-217D25362010", "9fa36c31-f92b-1d1e-9f4e-217d25362010", "12345"))
            error(chat(tokenA, bad, "Where is it?"), 400, "INVALID_CONVERSATION_ID");
        error(chat(tokenA, UUID.randomUUID().toString(), "Where is it?"), 404, "CONVERSATION_NOT_FOUND");

        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();
        redis.delete(KEY_PREFIX + id);  // what Redis does when the TTL runs out
        // Step 17 would restore it from the stored history (AssistantConversationHistoryIntegrationTests); without a
        // stored conversation an expired context is still simply gone.
        db.update("DELETE FROM assistant_messages WHERE conversation_id=?::uuid", id);
        db.update("DELETE FROM assistant_conversations WHERE id=?::uuid", id);
        var expired = error(chat(tokenA, id, "Who teaches the first one?"), 404, "CONVERSATION_NOT_FOUND");
        assertThat(expired).doesNotContain("CS301");
        assertThat(redis.hasKey(KEY_PREFIX + id)).isFalse();
        // conversationId must be a string; numbers or objects are rejected by the JSON binding.
        mvc.perform(post(CHAT).header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Hi\",\"conversationId\":{\"id\":1}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertThat(sent).hasSize(2);
    }

    @Test
    void anotherStudentCannotReadContinueOrExtendAConversation() throws Exception {
        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();
        String key = KEY_PREFIX + id;
        redis.expire(key, java.time.Duration.ofSeconds(100));
        String before = redis.opsForValue().get(key);

        String tokenB = login("STD-TEST-002");
        String body = error(chat(tokenB, id, "Who teaches the first one?"), 404, "CONVERSATION_NOT_FOUND");
        assertThat(body).doesNotContain("CS301").doesNotContain("CSC460").doesNotContain("What courses");

        assertThat(redis.opsForValue().get(key)).isEqualTo(before);
        assertThat(redis.getExpire(key)).isLessThanOrEqualTo(100);
        assertThat(sent).hasSize(2);  // only Student A's two provider calls
        // The same unknown-looking 404 as an id that never existed.
        assertThat(body).isEqualTo(error(chat(tokenB, UUID.randomUUID().toString(), "Hi"), 404, "CONVERSATION_NOT_FOUND"));
    }

    @Test
    void onlyACompletedTurnRefreshesTheTtlAndAFailedTurnChangesNothing() throws Exception {
        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();
        String key = KEY_PREFIX + id;
        redis.expire(key, java.time.Duration.ofSeconds(100));
        String before = redis.opsForValue().get(key);

        // An invalid message and a provider failure: no new history, no TTL refresh.
        error(chat(tokenA, id, " "), 400, "VALIDATION_ERROR");
        script.add(r -> { throw AiAssistantFailure.unavailable(); });
        error(chat(tokenA, id, "Who teaches the first one?"), 503, "AI_ASSISTANT_UNAVAILABLE");
        assertThat(redis.opsForValue().get(key)).isEqualTo(before);
        assertThat(redis.getExpire(key)).isLessThanOrEqualTo(100);

        askAboutReferent(GET_MY_COURSE_LECTURER, "lecturerNameEn");
        ok(chat(tokenA, id, "Who teaches the first one?"));
        assertThat(redis.getExpire(key)).isGreaterThan(1700);
        assertThat(stored(id).get("version").asLong()).isEqualTo(2);
    }

    // ---- Spec §58: concurrency ---------------------------------------------------------------------------------

    @Test
    void aTurnSavedMeanwhileMakesTheSlowerTurnFailWithConflict() throws Exception {
        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();
        String key = KEY_PREFIX + id;

        // While this request waits for the model, another request for the same conversation completes (version 2).
        script.add(r -> {
            JsonNode current = json.readTree(redis.opsForValue().get(key));
            var other = new LinkedHashMap<String, Object>(json.convertValue(current, Map.class));
            other.put("version", 2);
            redis.opsForValue().set(key, json.writeValueAsString(other), java.time.Duration.ofMinutes(30));
            // Fact-free on purpose: since Step 11 an answer naming a course or lecturer without evidence is retried.
            return text("Hello! What would you like to know about your courses?");
        });
        error(chat(tokenA, id, "Hello again"), 409, "CONVERSATION_CONFLICT");

        JsonNode stored = stored(id);
        assertThat(stored.get("version").asLong()).isEqualTo(2);
        assertThat(stored.get("messages")).hasSize(2);  // the slower turn was not appended over it
        assertThat(redis.getExpire(key)).isGreaterThan(0);
    }

    // ---- Spec §59/§60: privacy and size ------------------------------------------------------------------------

    @Test
    void storedConversationHoldsNoSecretsAndStaysWithinTheLimits() throws Exception {
        String id = null;
        String longQuestion = "ခရက်ဒစ် ".repeat(400);  // Myanmar text: about 3 bytes per character
        for (int turn = 0; turn < 8; turn++) {
            script.add(r -> text("A credit hour measures study time. " + "x".repeat(3000)));
            id = ok(chat(tokenA, id, turn == 7 ? longQuestion : "Question " + turn)).get("conversationId").stringValue();
        }
        String raw = redis.opsForValue().get(KEY_PREFIX + id);
        JsonNode stored = json.readTree(raw);
        assertThat(stored.get("version").asLong()).isEqualTo(8);
        // At most AI_CONTEXT_MAX_TURNS (6) pairs, newest kept, and never more than AI_CONTEXT_MAX_BYTES.
        assertThat(stored.get("messages").size()).isLessThanOrEqualTo(12).isEven();
        assertThat(stored.get("messages").get(stored.get("messages").size() - 2).get("content").stringValue()).startsWith("ခရက်ဒစ်");
        assertThat(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(32768);
        // The history sent to the model is the stored, bounded one.
        assertThat(sent.getLast().history().size()).isLessThanOrEqualTo(12);

        String hash = db.queryForObject("SELECT password_hash FROM student_credentials LIMIT 1", String.class);
        assertThat(raw).doesNotContain(tokenA).doesNotContain(hash).doesNotContain(AssistantAcademicFixture.PASSWORD)
                .doesNotContain("Bearer").doesNotContain("apiKey").doesNotContain("refresh");
        assertThat(redis.keys("*")).anyMatch(k -> k.startsWith("student:auth:session:"));  // auth sessions untouched
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    // The model lists the courses in its own order: CS301, CSC460, CSC452 (the database returns CS301, CSC452, CSC460).
    private void listCourses() {
        script.add(r -> tool(GET_MY_COURSES, Map.of()));
        script.add(r -> {
            var rows = (List<?>) ((Map<?, ?>) r.toolRounds().getLast().results().getFirst().result()).get("data");
            var names = new HashMap<String, String>();
            for (Object row : rows) names.put((String) ((Map<?, ?>) row).get("courseCode"), (String) ((Map<?, ?>) row).get("courseName"));
            assertThat(names.keySet()).containsExactlyInAnyOrder("CS301", "CSC452", "CSC460");
            return text("1. CS301 — " + names.get("CS301") + "\n2. CSC460 — " + names.get("CSC460") + "\n3. CSC452 — " + names.get("CSC452"));
        });
    }

    // Plays a model that uses the referent the backend resolved from the conversation context.
    private void askAboutReferent(String toolName, String field) {
        script.add(r -> {
            var m = REFERENT.matcher(r.systemPrompt());
            assertThat(m.find()).as("resolved referent in the system prompt").isTrue();
            return tool(toolName, Map.of("courseIdentifier", m.group(1)));
        });
        script.add(r -> text(answer(r, field)));
    }

    private static String answer(LlmRequest r, String field) {
        var payload = (Map<?, ?>) r.toolRounds().getLast().results().getFirst().result();
        if (!Boolean.TRUE.equals(payload.get("success"))) return "That information is not available: " + payload.get("code");
        var data = (Map<?, ?>) payload.get("data");
        // The room now comes from the section's published timetable (first meeting), not from a section column.
        Object value = "room".equals(field) ? PublishedMeetingTestData.room(data) : data.get(field);
        return data.get("courseCode") + ": " + (value == null ? "not available" : value);
    }

    private static Map<String, Object> toolArguments(LlmRequest request) {
        return request.toolRounds().getLast().assistantTurn().toolCalls().getFirst().arguments();
    }

    private static List<String> presented(JsonNode stored) {
        var codes = new ArrayList<String>();
        stored.get("academicReference").get("lastPresentedCourses").forEach(c -> codes.add(c.get("courseCode").stringValue()));
        return codes;
    }

    private static LlmResponse tool(String name, Map<String, Object> args) {
        return new LlmResponse(null, "scripted-model", null, List.of(new LlmToolCall("call-" + name, name, args)), null);
    }

    private static LlmResponse text(String content) { return new LlmResponse(content, "scripted-model", null); }

    private JsonNode stored(String id) { return json.readTree(redis.opsForValue().get(KEY_PREFIX + id)); }

    private MockHttpServletResponse chat(String token, String conversationId, String message) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("message", message);
        if (conversationId != null) body.put("conversationId", conversationId);
        return mvc.perform(post(CHAT).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsByteArray());
    }

    private String error(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        JsonNode body = json.readTree(response.getContentAsByteArray());
        assertThat(body.get("code").stringValue()).isEqualTo(code);
        return body.get("message").stringValue();
    }

    private String login(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password",
                                AssistantAcademicFixture.PASSWORD))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
}
