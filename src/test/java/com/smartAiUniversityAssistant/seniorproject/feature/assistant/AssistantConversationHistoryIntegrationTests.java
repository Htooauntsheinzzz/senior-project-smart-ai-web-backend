package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.ConversationContextService;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Step 17 (spec file 58) through the real endpoints, JWT, Redis and PostgreSQL (Testcontainers). Only the model is
 * scripted. Checks that completed turns are stored, that a Student can list, read, continue and delete their own
 * conversations, and that another Student's conversation always looks like a missing one.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AssistantConversationHistoryIntegrationTests extends IntegrationSupport {
    private static final String CHAT = "/api/v1/student/assistant/chat";
    private static final String CONVERSATIONS = "/api/v1/student/assistant/conversations";
    private static final String KEY_PREFIX = "student:assistant:conversation:";
    private static final Pattern REFERENT = Pattern.compile("(?:which is|refers to) ([A-Z]{2,5}\\d{3,4})\\.");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired ConversationContextService contexts;
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

    // The fixture removes the stored conversations before the Students (the schema has no cascading foreign key).
    @AfterEach
    void cleanup() {
        fixture.clean();
        assertThat(count("assistant_conversations")).isZero();
        assertThat(count("assistant_messages")).isZero();
    }

    // ---- storing ---------------------------------------------------------------------------------------------------

    @Test
    void completedTurnsAreStoredWithTitlePreviewAndCount() throws Exception {
        listCourses();
        JsonNode first = ok(chat(tokenA, null, "  What   courses\ndo I have?  "));
        String id = first.get("conversationId").stringValue();

        Map<String, Object> row = db.queryForMap("SELECT * FROM assistant_conversations WHERE id=?::uuid", id);
        assertThat(row.get("student_id")).isEqualTo(fixture.studentA());
        assertThat(row.get("title")).isEqualTo("What courses do I have?");
        assertThat(row.get("message_count")).isEqualTo(2);
        assertThat(row.get("context_version")).isEqualTo(1L);
        assertThat((String) row.get("last_message_preview")).isEqualTo("1. CS301 — Data Structures 2. CSC460 — Cloud Computing 3. CSC452 — Mobile Programming");
        assertThat((String) row.get("academic_reference")).contains("CS301").contains(GET_MY_COURSES);
        var messages = db.queryForList("SELECT role,content,request_id,model,sources FROM assistant_messages WHERE conversation_id=?::uuid ORDER BY id", id);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).containsEntry("role", "USER").containsEntry("content", "What   courses\ndo I have?")
                .containsEntry("request_id", first.get("requestId").stringValue()).containsEntry("model", null);
        assertThat(messages.get(1)).containsEntry("role", "ASSISTANT").containsEntry("content", first.get("message").stringValue())
                .containsEntry("request_id", first.get("requestId").stringValue()).containsEntry("model", "scripted-model")
                .containsEntry("sources", null);

        askAboutReferent(GET_MY_COURSE_LECTURER, "lecturerNameEn");
        ok(chat(tokenA, id, "Who teaches the first one?"));
        row = db.queryForMap("SELECT * FROM assistant_conversations WHERE id=?::uuid", id);
        assertThat(row.get("title")).isEqualTo("What courses do I have?");  // the title stays the first question
        assertThat(row.get("message_count")).isEqualTo(4);
        assertThat(row.get("context_version")).isEqualTo(2L);
        assertThat(row.get("last_message_preview")).isEqualTo("CS301: Dr. John Smith");
        assertThat(count("assistant_conversations")).isEqualTo(1);
        assertThat(count("assistant_messages")).isEqualTo(4);
        // Tool results and prompts are not part of the history.
        assertThat(db.queryForList("SELECT content FROM assistant_messages", String.class))
                .noneMatch(c -> c.contains("courseSectionId") || c.contains("ACADEMIC FACT RULES"));
    }

    @Test
    void aLongFirstQuestionBecomesAShortTitle() throws Exception {
        script.add(r -> text("A credit hour measures study time. " + "y".repeat(400)));
        String id = ok(chat(tokenA, null, "Please explain " + "very ".repeat(40) + "carefully what a credit hour is"))
                .get("conversationId").stringValue();
        Map<String, Object> row = db.queryForMap("SELECT title,last_message_preview FROM assistant_conversations WHERE id=?::uuid", id);
        assertThat((String) row.get("title")).hasSizeLessThanOrEqualTo(80).startsWith("Please explain very").endsWith("…");
        assertThat((String) row.get("last_message_preview")).hasSizeLessThanOrEqualTo(160).endsWith("…");
        // The stored message itself is complete.
        assertThat(db.queryForObject("SELECT length(content) FROM assistant_messages WHERE role='ASSISTANT'", Integer.class)).isEqualTo(435);
    }

    @Test
    void aFailedOrConflictingRequestStoresNothing() throws Exception {
        script.add(r -> { throw AiAssistantFailure.unavailable(); });
        error(chat(tokenA, null, "What is a credit hour?"), 503, "AI_ASSISTANT_UNAVAILABLE");
        assertThat(count("assistant_conversations")).isZero();
        assertThat(count("assistant_messages")).isZero();

        script.add(r -> text("A credit hour measures study time."));
        String id = ok(chat(tokenA, null, "What is a credit hour?")).get("conversationId").stringValue();
        String key = KEY_PREFIX + id;
        // Another turn of the same conversation is saved while this one waits for the model: the Redis version check
        // refuses the slower turn, and its rows are rolled back.
        script.add(r -> {
            var other = new LinkedHashMap<String, Object>(json.convertValue(json.readTree(redis.opsForValue().get(key)), Map.class));
            other.put("version", 2);
            redis.opsForValue().set(key, json.writeValueAsString(other), java.time.Duration.ofMinutes(30));
            return text("Hello! What would you like to know?");
        });
        error(chat(tokenA, id, "Hello again"), 409, "CONVERSATION_CONFLICT");
        assertThat(count("assistant_messages")).isEqualTo(2);
        assertThat(db.queryForObject("SELECT message_count FROM assistant_conversations WHERE id=?::uuid", Integer.class, id)).isEqualTo(2);
    }

    // ---- list ------------------------------------------------------------------------------------------------------

    @Test
    void listShowsOnlyTheStudentsOwnConversationsNewestFirst() throws Exception {
        String first = talk(tokenA, null, "First question", "First answer");
        clock.set(clock.instant().plusSeconds(60));
        String second = talk(tokenA, null, "Second question", "Second answer");
        clock.set(clock.instant().plusSeconds(60));
        String tokenB = login("STD-TEST-002");
        String other = talk(tokenB, null, "A question of student B", "An answer for student B");
        clock.set(clock.instant().plusSeconds(60));
        talk(tokenA, first, "First again", "First answer again");  // the older conversation becomes the newest

        var response = perform(get(CONVERSATIONS), tokenA);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        JsonNode page = ok(response);
        assertThat(ids(page)).containsExactly(first, second);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("first").asBoolean()).isTrue();
        assertThat(page.get("last").asBoolean()).isTrue();
        JsonNode newest = page.get("content").get(0);
        assertThat(newest.get("title").stringValue()).isEqualTo("First question");
        assertThat(newest.get("lastMessagePreview").stringValue()).isEqualTo("First answer again");
        assertThat(newest.get("messageCount").asInt()).isEqualTo(4);
        assertThat(Instant.parse(newest.get("updatedAt").stringValue())).isAfter(Instant.parse(newest.get("createdAt").stringValue()));
        assertThat(page.toString()).doesNotContain("student B").doesNotContain("studentId").doesNotContain("academicReference");

        JsonNode secondPage = ok(perform(get(CONVERSATIONS).param("page", "1").param("size", "1"), tokenA));
        assertThat(ids(secondPage)).containsExactly(second);
        assertThat(secondPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(secondPage.get("last").asBoolean()).isTrue();

        assertThat(ids(ok(perform(get(CONVERSATIONS), tokenB)))).containsExactly(other);
        // Student C has none.
        assertThat(ok(perform(get(CONVERSATIONS), login("STD-TEST-003"))).get("content")).isEmpty();
    }

    // ---- get -------------------------------------------------------------------------------------------------------

    @Test
    void getReturnsTheMessagesInOrderAndInWindows() throws Exception {
        String id = talk(tokenA, null, "Question 1", "Answer 1");
        talk(tokenA, id, "Question 2", "Answer 2");
        talk(tokenA, id, "Question 3", "Answer 3");

        JsonNode all = ok(perform(get(CONVERSATIONS + "/" + id), tokenA));
        assertThat(all.get("conversationId").stringValue()).isEqualTo(id);
        assertThat(all.get("title").stringValue()).isEqualTo("Question 1");
        assertThat(all.get("messageCount").asInt()).isEqualTo(6);
        assertThat(all.get("hasMoreBefore").asBoolean()).isFalse();
        assertThat(contents(all)).containsExactly("Question 1", "Answer 1", "Question 2", "Answer 2", "Question 3", "Answer 3");
        assertThat(all.get("messages").get(0).get("role").stringValue()).isEqualTo("USER");
        assertThat(all.get("messages").get(1).get("role").stringValue()).isEqualTo("ASSISTANT");
        assertThat(all.get("messages").get(1).get("sources").isArray()).isTrue();
        assertThat(all.get("messages").get(1).get("sources")).isEmpty();
        assertThat(all.toString()).doesNotContain("requestId").doesNotContain("scripted-model");

        JsonNode newest = ok(perform(get(CONVERSATIONS + "/" + id).param("limit", "2"), tokenA));
        assertThat(contents(newest)).containsExactly("Question 3", "Answer 3");
        assertThat(newest.get("hasMoreBefore").asBoolean()).isTrue();
        String before = newest.get("messages").get(0).get("messageId").asString();
        JsonNode older = ok(perform(get(CONVERSATIONS + "/" + id).param("limit", "3").param("beforeMessageId", before), tokenA));
        assertThat(contents(older)).containsExactly("Answer 1", "Question 2", "Answer 2");
        assertThat(older.get("hasMoreBefore").asBoolean()).isTrue();
        before = older.get("messages").get(0).get("messageId").asString();
        JsonNode oldest = ok(perform(get(CONVERSATIONS + "/" + id).param("beforeMessageId", before), tokenA));
        assertThat(contents(oldest)).containsExactly("Question 1");
        assertThat(oldest.get("hasMoreBefore").asBoolean()).isFalse();
    }

    @Test
    void theSourcesOfAnAnswerAreStoredAndReturned() throws Exception {
        var context = contexts.createNew(fixture.studentA());
        var sources = List.of(new StudentAssistantChatResponse.Source("Academic Regulations 2026", "2026-2027", "Regulations"),
                new StudentAssistantChatResponse.Source("Student Handbook", null, "Handbook"));
        contexts.saveCompletedTurn(context, ConversationMessage.user("How many credits may I take?", clock.instant()),
                ConversationMessage.assistant("You may take 9 to 22 credits.", clock.instant()), null,
                new AssistantTurnDetails(UUID.randomUUID().toString(), "scripted-model", sources));

        JsonNode detail = ok(perform(get(CONVERSATIONS + "/" + context.conversationId()), tokenA));
        assertThat(detail.get("messages").get(0).get("sources")).isEmpty();
        JsonNode stored = detail.get("messages").get(1).get("sources");
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).get("title").stringValue()).isEqualTo("Academic Regulations 2026");
        assertThat(stored.get(0).get("academicYear").stringValue()).isEqualTo("2026-2027");
        assertThat(stored.get(0).get("category").stringValue()).isEqualTo("Regulations");
        assertThat(stored.get(1).get("academicYear").isNull()).isTrue();
    }

    // ---- continue --------------------------------------------------------------------------------------------------

    @Test
    void anExpiredConversationIsRestoredFromTheHistoryAndContinued() throws Exception {
        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();
        redis.delete(KEY_PREFIX + id);  // what Redis does when the TTL runs out

        askAboutReferent(GET_MY_COURSE_LECTURER, "lecturerNameEn");
        JsonNode second = ok(chat(tokenA, id, "Who teaches the first one?"));
        assertThat(second.get("conversationId").stringValue()).isEqualTo(id);
        assertThat(second.get("message").stringValue()).isEqualTo("CS301: Dr. John Smith");
        // The model received the stored turn as history and the restored reference ("the first one" = CS301).
        LlmRequest followUp = sent.get(sent.size() - 2);
        assertThat(followUp.history()).extracting(ConversationMessage::role).containsExactly("USER", "ASSISTANT");
        assertThat(followUp.history().getFirst().content()).isEqualTo("What courses do I have?");
        assertThat(followUp.systemPrompt()).contains("item 1 of that list, which is CS301.");

        JsonNode context = json.readTree(redis.opsForValue().get(KEY_PREFIX + id));
        assertThat(context.get("version").asLong()).isEqualTo(2);
        assertThat(context.get("messages")).hasSize(4);
        assertThat(context.get("studentId").asLong()).isEqualTo(fixture.studentA());
        assertThat(redis.getExpire(KEY_PREFIX + id)).isBetween(1700L, 1800L);
        assertThat(db.queryForObject("SELECT message_count FROM assistant_conversations WHERE id=?::uuid", Integer.class, id)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT context_version FROM assistant_conversations WHERE id=?::uuid", Long.class, id)).isEqualTo(2);
    }

    @Test
    void aRestoredContextKeepsOnlyTheNewestTurns() throws Exception {
        String id = null;
        for (int turn = 1; turn <= 8; turn++) id = talk(tokenA, id, "Question " + turn, "Answer " + turn);
        redis.delete(KEY_PREFIX + id);

        talk(tokenA, id, "Question 9", "Answer 9");
        // AI_CONTEXT_MAX_TURNS is 6: turns 3 to 8 are sent, the full history stays in PostgreSQL.
        assertThat(sent.getLast().history()).hasSize(12);
        assertThat(sent.getLast().history().getFirst().content()).isEqualTo("Question 3");
        assertThat(sent.getLast().history().getLast().content()).isEqualTo("Answer 8");
        assertThat(json.readTree(redis.opsForValue().get(KEY_PREFIX + id)).get("version").asLong()).isEqualTo(9);
        assertThat(ok(perform(get(CONVERSATIONS + "/" + id).param("limit", "100"), tokenA)).get("messages")).hasSize(18);
    }

    // ---- isolation -------------------------------------------------------------------------------------------------

    @Test
    void anotherStudentsConversationLooksLikeAMissingOne() throws Exception {
        listCourses();
        String id = ok(chat(tokenA, null, "What courses do I have?")).get("conversationId").stringValue();
        String tokenB = login("STD-TEST-002");
        String missing = UUID.randomUUID().toString();

        String notFound = error(perform(get(CONVERSATIONS + "/" + missing), tokenB), 404, "CONVERSATION_NOT_FOUND");
        assertThat(error(perform(get(CONVERSATIONS + "/" + id), tokenB), 404, "CONVERSATION_NOT_FOUND")).isEqualTo(notFound);
        error(perform(delete(CONVERSATIONS + "/" + id), tokenB), 404, "CONVERSATION_NOT_FOUND");
        // With the Redis value present (owner check of Step 10) and after it expired (no restore for another Student).
        error(chat(tokenB, id, "Who teaches the first one?"), 404, "CONVERSATION_NOT_FOUND");
        redis.delete(KEY_PREFIX + id);
        String reply = error(chat(tokenB, id, "Who teaches the first one?"), 404, "CONVERSATION_NOT_FOUND");
        assertThat(reply).doesNotContain("CS301");
        assertThat(redis.hasKey(KEY_PREFIX + id)).isFalse();
        assertThat(script).isEmpty();
        assertThat(sent).hasSize(2);  // only Student A's first question reached the model

        // Nothing changed for Student A.
        assertThat(count("assistant_messages")).isEqualTo(2);
        assertThat(contents(ok(perform(get(CONVERSATIONS + "/" + id), tokenA)))).hasSize(2);
    }

    // ---- delete ----------------------------------------------------------------------------------------------------

    @Test
    void deleteRemovesTheHistoryAndTheContext() throws Exception {
        String id = talk(tokenA, null, "Question 1", "Answer 1");
        String kept = talk(tokenA, null, "Another conversation", "Another answer");
        assertThat(redis.hasKey(KEY_PREFIX + id)).isTrue();

        var response = perform(delete(CONVERSATIONS + "/" + id), tokenA);
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(redis.hasKey(KEY_PREFIX + id)).isFalse();
        assertThat(redis.hasKey(KEY_PREFIX + kept)).isTrue();
        assertThat(count("assistant_conversations")).isEqualTo(1);
        assertThat(count("assistant_messages")).isEqualTo(2);
        assertThat(ids(ok(perform(get(CONVERSATIONS), tokenA)))).containsExactly(kept);

        error(perform(get(CONVERSATIONS + "/" + id), tokenA), 404, "CONVERSATION_NOT_FOUND");
        error(perform(delete(CONVERSATIONS + "/" + id), tokenA), 404, "CONVERSATION_NOT_FOUND");
        error(chat(tokenA, id, "Question 2"), 404, "CONVERSATION_NOT_FOUND");
        assertThat(sent).hasSize(2);  // the deleted conversation cost no model request
    }

    // ---- validation and authentication -----------------------------------------------------------------------------

    @Test
    void invalidIdsAndParametersAreRejected() throws Exception {
        String id = talk(tokenA, null, "Question 1", "Answer 1");
        for (String bad : List.of("not-a-uuid", "12345", "9FA36C31-F92B-4D1E-9F4E-217D25362010", "9fa36c31-f92b-1d1e-9f4e-217d25362010")) {
            error(perform(get(CONVERSATIONS + "/" + bad), tokenA), 400, "INVALID_CONVERSATION_ID");
            error(perform(delete(CONVERSATIONS + "/" + bad), tokenA), 400, "INVALID_CONVERSATION_ID");
        }
        for (String[] bad : List.of(new String[] {"page", "-1"}, new String[] {"size", "0"}, new String[] {"size", "51"},
                new String[] {"page", "x"}))
            error(perform(get(CONVERSATIONS).param(bad[0], bad[1]), tokenA), 400, "VALIDATION_ERROR");
        for (String[] bad : List.of(new String[] {"limit", "0"}, new String[] {"limit", "101"}, new String[] {"beforeMessageId", "0"},
                new String[] {"beforeMessageId", "abc"}))
            error(perform(get(CONVERSATIONS + "/" + id).param(bad[0], bad[1]), tokenA), 400, "VALIDATION_ERROR");

        assertThat(mvc.perform(get(CONVERSATIONS)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(get(CONVERSATIONS + "/" + id)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(delete(CONVERSATIONS + "/" + id)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(count("assistant_conversations")).isEqualTo(1);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    // One turn answered by the model in plain text; returns the conversationId.
    private String talk(String token, String conversationId, String question, String answer) throws Exception {
        script.add(r -> text(answer));
        return ok(chat(token, conversationId, question)).get("conversationId").stringValue();
    }

    // The model lists the courses in its own order: CS301, CSC460, CSC452.
    private void listCourses() {
        script.add(r -> tool(GET_MY_COURSES, Map.of()));
        script.add(r -> {
            var rows = (List<?>) ((Map<?, ?>) r.toolRounds().getLast().results().getFirst().result()).get("data");
            var names = new HashMap<String, String>();
            for (Object row : rows) names.put((String) ((Map<?, ?>) row).get("courseCode"), (String) ((Map<?, ?>) row).get("courseName"));
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
        script.add(r -> {
            var data = (Map<?, ?>) ((Map<?, ?>) r.toolRounds().getLast().results().getFirst().result()).get("data");
            return text(data.get("courseCode") + ": " + data.get(field));
        });
    }

    private static LlmResponse tool(String name, Map<String, Object> args) {
        return new LlmResponse(null, "scripted-model", null, List.of(new LlmToolCall("call-" + name, name, args)), null);
    }

    private static LlmResponse text(String content) { return new LlmResponse(content, "scripted-model", null); }

    private long count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    private static List<String> ids(JsonNode page) {
        var ids = new ArrayList<String>();
        page.get("content").forEach(c -> ids.add(c.get("conversationId").stringValue()));
        return ids;
    }

    private static List<String> contents(JsonNode detail) {
        var contents = new ArrayList<String>();
        detail.get("messages").forEach(m -> contents.add(m.get("content").stringValue()));
        return contents;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

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
