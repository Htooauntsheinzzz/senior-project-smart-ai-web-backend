package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
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
 * Spec file 59 through the real endpoints, JWT, Redis and PostgreSQL (Testcontainers): the Admin Chat History and AI
 * Feedback APIs and the Student's feedback endpoint. Conversations are created through the Student chat endpoint with a
 * scripted model, so the rows are exactly what spec file 58 stores.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AdminAssistantHistoryAndFeedbackIntegrationTests extends IntegrationSupport {
    private static final String CHAT = "/api/v1/student/assistant/chat";
    private static final String STUDENT_CONVERSATIONS = "/api/v1/student/assistant/conversations";
    private static final String CONVERSATIONS = "/api/v1/admin/assistant/conversations";
    private static final String FEEDBACK = "/api/v1/admin/assistant/feedback";
    private static final String ADMIN_PASSWORD = "spec 59 admin password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean LlmClient llm;
    private AssistantAcademicFixture fixture;
    private final Deque<String> answers = new ArrayDeque<>();
    private String tokenA, tokenB, admin;
    private long adminId;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2054-11-10T03:00:00Z"));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        fixture = new AssistantAcademicFixture(db, encoder);
        fixture.create();
        db.update("UPDATE students SET first_name='Alice',last_name='Anders' WHERE id=?", fixture.studentA());
        db.update("UPDATE students SET first_name='Bob',last_name='Brown' WHERE id=?", fixture.studentB());
        adminId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('S59-ADMIN','Review','Admin','spec59.admin@example.test','ACTIVE') RETURNING id""", Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", adminId,
                encoder.encode(ADMIN_PASSWORD));
        setRole("ADMIN");
        answers.clear();
        when(llm.chat(any())).thenAnswer(invocation -> {
            String answer = answers.poll();
            assertThat(answer).as("unexpected provider call").isNotNull();
            return new LlmResponse(answer, "scripted-model", null);
        });
        tokenA = studentLogin("STD-TEST-001");
        tokenB = studentLogin("STD-TEST-002");
        admin = adminLogin();
    }

    @AfterEach
    void cleanup() { fixture.clean(); }

    // ---- chat history ----------------------------------------------------------------------------------------------

    @Test
    void adminsListEveryStudentsConversationsNewestFirstWithSearchAndDates() throws Exception {
        String first = talk(tokenA, null, "What is a credit hour?", "A credit hour measures weekly study time.");
        advance(Duration.ofHours(1));
        String second = talk(tokenB, null, "How do I contact the library?", "Please ask the library desk.");
        advance(Duration.ofDays(2));
        String third = talk(tokenA, null, "Explain prerequisites", "A prerequisite is a course to pass first.");

        JsonNode page = ok(get(CONVERSATIONS));
        assertThat(ids(page)).containsExactly(third, second, first);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("size").asInt()).isEqualTo(10);
        assertThat(page.get("first").asBoolean()).isTrue();
        assertThat(page.get("last").asBoolean()).isTrue();
        JsonNode row = page.get("content").get(1);
        assertThat(row.get("title").stringValue()).isEqualTo("How do I contact the library?");
        assertThat(row.get("lastMessagePreview").stringValue()).isEqualTo("Please ask the library desk.");
        assertThat(row.get("messageCount").asInt()).isEqualTo(2);
        assertThat(row.get("student").get("studentCode").stringValue()).isEqualTo("STD-TEST-002");
        assertThat(row.get("student").get("fullName").stringValue()).isEqualTo("Bob Brown");
        assertThat(row.get("student").get("id").asLong()).isEqualTo(fixture.studentB());
        // The stored academic reference and context version are never part of an Admin response.
        assertThat(row.has("academicReference")).isFalse();

        assertThat(ids(ok(get(CONVERSATIONS).param("size", "2")))).containsExactly(third, second);
        JsonNode pageTwo = ok(get(CONVERSATIONS).param("size", "2").param("page", "1"));
        assertThat(ids(pageTwo)).containsExactly(first);
        assertThat(pageTwo.get("totalPages").asInt()).isEqualTo(2);
        assertThat(pageTwo.get("last").asBoolean()).isTrue();

        // Search: title, Student code, Student name and message text, case-insensitive; % and _ match themselves.
        assertThat(ids(ok(get(CONVERSATIONS).param("search", "LIBRARY")))).containsExactly(second);
        assertThat(ids(ok(get(CONVERSATIONS).param("search", "std-test-001")))).containsExactly(third, first);
        assertThat(ids(ok(get(CONVERSATIONS).param("search", "bob br")))).containsExactly(second);
        assertThat(ids(ok(get(CONVERSATIONS).param("search", "weekly study")))).containsExactly(first);
        assertThat(ids(ok(get(CONVERSATIONS).param("search", "%")))).isEmpty();
        assertThat(ids(ok(get(CONVERSATIONS).param("search", "no such text")))).isEmpty();

        // Dates filter on the latest activity: from inclusive, to exclusive.
        assertThat(ids(ok(get(CONVERSATIONS).param("from", "2054-11-11T00:00:00Z")))).containsExactly(third);
        assertThat(ids(ok(get(CONVERSATIONS).param("to", "2054-11-10T04:00:00Z")))).containsExactly(first);
        assertThat(ids(ok(get(CONVERSATIONS).param("from", "2054-11-10T04:00:00Z").param("to", "2054-11-11T00:00:00Z"))))
                .containsExactly(second);
    }

    @Test
    void adminsReadATranscriptWithSendersSourcesAndRatings() throws Exception {
        String id = talk(tokenA, null, "Question 1", "Answer 1");
        talk(tokenA, id, "Question 2", "Answer 2");
        long answerTwo = db.queryForObject("SELECT max(id) FROM assistant_messages WHERE conversation_id=?::uuid", Long.class, id);
        db.update("UPDATE assistant_messages SET sources=? WHERE id=?",
                "[{\"title\":\"Student Handbook\",\"academicYear\":\"2026-2027\",\"category\":\"Regulations\"}]", answerTwo);
        ok(rate(tokenA, id, Map.of("messageId", answerTwo, "rating", "NEGATIVE", "comment", "Wrong fee")));

        JsonNode detail = ok(get(CONVERSATIONS + "/" + id));
        assertThat(detail.get("title").stringValue()).isEqualTo("Question 1");
        assertThat(detail.get("messageCount").asInt()).isEqualTo(4);
        assertThat(detail.get("student").get("fullName").stringValue()).isEqualTo("Alice Anders");
        assertThat(detail.get("hasMoreBefore").asBoolean()).isFalse();
        JsonNode messages = detail.get("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0).get("role").stringValue()).isEqualTo("USER");
        assertThat(messages.get(0).get("content").stringValue()).isEqualTo("Question 1");
        assertThat(messages.get(0).get("model").isNull()).isTrue();
        assertThat(messages.get(0).get("feedback").isNull()).isTrue();
        assertThat(messages.get(1).get("model").stringValue()).isEqualTo("scripted-model");
        assertThat(messages.get(1).get("sources")).isEmpty();
        assertThat(messages.get(1).get("feedback").isNull()).isTrue();
        JsonNode rated = messages.get(3);
        assertThat(rated.get("content").stringValue()).isEqualTo("Answer 2");
        assertThat(rated.get("sources").get(0).get("title").stringValue()).isEqualTo("Student Handbook");
        assertThat(rated.get("feedback").get("rating").stringValue()).isEqualTo("NEGATIVE");
        assertThat(rated.get("feedback").get("comment").stringValue()).isEqualTo("Wrong fee");
        assertThat(rated.get("feedback").get("reviewStatus").stringValue()).isEqualTo("PENDING");

        // Windows of the transcript, like the Student endpoint.
        JsonNode newest = ok(get(CONVERSATIONS + "/" + id).param("limit", "2"));
        assertThat(newest.get("hasMoreBefore").asBoolean()).isTrue();
        assertThat(newest.get("messages").get(0).get("content").stringValue()).isEqualTo("Question 2");
        JsonNode older = ok(get(CONVERSATIONS + "/" + id).param("limit", "2")
                .param("beforeMessageId", newest.get("messages").get(0).get("messageId").asString()));
        assertThat(older.get("hasMoreBefore").asBoolean()).isFalse();
        assertThat(older.get("messages").get(1).get("content").stringValue()).isEqualTo("Answer 1");

        error(perform(get(CONVERSATIONS + "/" + UUID.randomUUID()), admin), 404, "CONVERSATION_NOT_FOUND");
        error(perform(get(CONVERSATIONS + "/not-a-uuid"), admin), 400, "INVALID_CONVERSATION_ID");
        // Admins only read: the Student's data is unchanged and there is no Admin delete.
        assertThat(perform(delete(CONVERSATIONS + "/" + id), admin).getStatus()).isIn(404, 405);
        assertThat(count("assistant_messages")).isEqualTo(4);
    }

    @Test
    void historyValidationRolesAndExport() throws Exception {
        String id = talk(tokenA, null, "=SUM(A1) is this a formula?", "No, \"quoted\" text.");
        for (String[] bad : List.of(new String[] {"page", "-1"}, new String[] {"size", "0"}, new String[] {"size", "51"},
                new String[] {"page", "x"}, new String[] {"from", "2054-11-10"}, new String[] {"to", "yesterday"},
                new String[] {"search", "x".repeat(101)}))
            error(perform(get(CONVERSATIONS).param(bad[0], bad[1]), admin), 400, "VALIDATION_ERROR");
        error(perform(get(CONVERSATIONS).param("from", "2054-11-11T00:00:00Z").param("to", "2054-11-10T00:00:00Z"), admin),
                400, "VALIDATION_ERROR");
        for (String[] bad : List.of(new String[] {"limit", "0"}, new String[] {"limit", "101"}, new String[] {"beforeMessageId", "0"}))
            error(perform(get(CONVERSATIONS + "/" + id).param(bad[0], bad[1]), admin), 400, "VALIDATION_ERROR");

        // No token, and a Student token, never reach the Admin endpoints.
        for (String path : List.of(CONVERSATIONS, CONVERSATIONS + "/" + id, CONVERSATIONS + "/export", FEEDBACK, FEEDBACK + "/statistics",
                FEEDBACK + "/export")) {
            assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).as(path).isEqualTo(401);
            assertThat(perform(get(path), tokenA).getStatus()).as(path).isEqualTo(403);
        }
        for (String role : List.of("SUPER_ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            assertThat(perform(get(CONVERSATIONS), adminLogin()).getStatus()).as(role).isEqualTo(200);
        }

        MockHttpServletResponse export = perform(get(CONVERSATIONS + "/export"), admin);
        assertThat(export.getStatus()).isEqualTo(200);
        assertThat(export.getContentType()).startsWith("text/csv");
        assertThat(export.getHeader("Content-Disposition")).contains("attachment").contains("ai-chat-history.csv");
        String csv = new String(export.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿\"No.\",\"Conversation\",\"Student Code\"");
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(2);
        // A title that a spreadsheet would run as a formula is neutralised; quotes are doubled.
        assertThat(lines[1]).startsWith("\"1\",\"'=SUM(A1) is this a formula?\",\"STD-TEST-001\",\"Alice Anders\",\"2\"")
                .contains("\"No, \"\"quoted\"\" text.\"").endsWith("\"" + id + "\"");
        assertThat(new String(perform(get(CONVERSATIONS + "/export").param("search", "nothing"), admin).getContentAsByteArray(),
                StandardCharsets.UTF_8).split("\r\n")).hasSize(1);
    }

    // ---- feedback: Student -----------------------------------------------------------------------------------------

    @Test
    void aStudentRatesTheirOwnAnswersByMessageIdOrRequestId() throws Exception {
        JsonNode chat = chat(tokenA, null, "Question 1", "Answer 1");
        String id = chat.get("conversationId").stringValue();
        long answer = db.queryForObject("SELECT id FROM assistant_messages WHERE conversation_id=?::uuid AND role='ASSISTANT'", Long.class, id);
        long question = db.queryForObject("SELECT id FROM assistant_messages WHERE conversation_id=?::uuid AND role='USER'", Long.class, id);

        JsonNode saved = ok(rate(tokenA, id, Map.of("requestId", chat.get("requestId").stringValue(), "rating", "POSITIVE")));
        assertThat(saved.get("messageId").asLong()).isEqualTo(answer);
        assertThat(saved.get("rating").stringValue()).isEqualTo("POSITIVE");
        assertThat(saved.get("comment").isNull()).isTrue();
        assertThat(count("assistant_message_feedback")).isEqualTo(1);

        // An Admin reviews it; rating the answer again replaces the rating and reopens the review.
        long feedbackId = db.queryForObject("SELECT id FROM assistant_message_feedback", Long.class);
        ok(patch(FEEDBACK + "/" + feedbackId + "/review"), Map.of("reviewStatus", "RESOLVED", "reviewNote", "Fine"));
        advance(Duration.ofMinutes(5));
        JsonNode again = ok(rate(tokenA, id, Map.of("messageId", answer, "rating", "NEGATIVE", "comment", "  The fee is wrong  ")));
        assertThat(again.get("comment").stringValue()).isEqualTo("The fee is wrong");
        assertThat(count("assistant_message_feedback")).isEqualTo(1);
        Map<String, Object> row = db.queryForMap("SELECT * FROM assistant_message_feedback");
        assertThat(row).containsEntry("rating", "NEGATIVE").containsEntry("review_status", "PENDING")
                .containsEntry("review_note", null).containsEntry("reviewed_by", null).containsEntry("student_id", fixture.studentA());

        // Only answers of the Student's own conversation can be rated.
        error(rate(tokenA, id, Map.of("messageId", question, "rating", "POSITIVE")), 404, "ASSISTANT_MESSAGE_NOT_FOUND");
        error(rate(tokenA, id, Map.of("messageId", 999_999_999L, "rating", "POSITIVE")), 404, "ASSISTANT_MESSAGE_NOT_FOUND");
        error(rate(tokenA, id, Map.of("requestId", UUID.randomUUID().toString(), "rating", "POSITIVE")), 404, "ASSISTANT_MESSAGE_NOT_FOUND");
        error(rate(tokenB, id, Map.of("messageId", answer, "rating", "POSITIVE")), 404, "CONVERSATION_NOT_FOUND");
        String other = talk(tokenB, null, "Other question", "Other answer");
        error(rate(tokenB, other, Map.of("messageId", answer, "rating", "POSITIVE")), 404, "ASSISTANT_MESSAGE_NOT_FOUND");
        error(rate(tokenA, "not-a-uuid", Map.of("messageId", answer, "rating", "POSITIVE")), 400, "INVALID_CONVERSATION_ID");
        for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of("messageId", answer), Map.of("messageId", answer, "rating", "GOOD"),
                Map.of("rating", "POSITIVE"), Map.of("messageId", answer, "requestId", "r", "rating", "POSITIVE"),
                Map.of("messageId", answer, "rating", "POSITIVE", "comment", "x".repeat(1001)),
                Map.of("messageId", answer, "rating", "POSITIVE", "studentId", 1)))
            error(rate(tokenA, id, bad), 400, "VALIDATION_ERROR");
        assertThat(mvc.perform(put(STUDENT_CONVERSATIONS + "/" + id + "/feedback").contentType(MediaType.APPLICATION_JSON)
                .content("{\"messageId\":1,\"rating\":\"POSITIVE\"}")).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(rate(admin, id, Map.of("messageId", answer, "rating", "POSITIVE")).getStatus()).isEqualTo(403);
        assertThat(db.queryForObject("SELECT rating FROM assistant_message_feedback", String.class)).isEqualTo("NEGATIVE");

        // Deleting the conversation removes its feedback too.
        assertThat(perform(delete(STUDENT_CONVERSATIONS + "/" + id), tokenA).getStatus()).isEqualTo(204);
        assertThat(count("assistant_message_feedback")).isZero();
        assertThat(count("assistant_messages")).isEqualTo(2);  // Student B's conversation is untouched
    }

    // ---- feedback: Admin -------------------------------------------------------------------------------------------

    @Test
    void adminsListFilterReviewAndExportFeedback() throws Exception {
        JsonNode empty = ok(get(FEEDBACK));
        assertThat(empty.get("content")).isEmpty();
        assertThat(empty.get("totalElements").asLong()).isZero();
        assertThat(ok(get(FEEDBACK + "/statistics")).get("totalFeedback").asLong()).isZero();

        String conversationA = talk(tokenA, null, "How should I prepare for a presentation?", "Practise aloud twice. " + "x".repeat(400));
        long positive = rateLatest(tokenA, conversationA, "POSITIVE", null);
        advance(Duration.ofHours(2));
        talk(tokenA, conversationA, "Can you suggest a study technique?", "Try spaced repetition.");
        long negative = rateLatest(tokenA, conversationA, "NEGATIVE", "The handbook says 1,000 baht");
        advance(Duration.ofDays(1));
        String conversationB = talk(tokenB, null, "Where is the library?", "Building 7.");
        long third = rateLatest(tokenB, conversationB, "NEGATIVE", null);

        JsonNode page = ok(get(FEEDBACK));
        assertThat(feedbackIds(page)).containsExactly(third, negative, positive);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        JsonNode row = page.get("content").get(1);
        assertThat(row.get("rating").stringValue()).isEqualTo("NEGATIVE");
        assertThat(row.get("comment").stringValue()).isEqualTo("The handbook says 1,000 baht");
        assertThat(row.get("reviewStatus").stringValue()).isEqualTo("PENDING");
        assertThat(row.get("reviewedBy").isNull()).isTrue();
        assertThat(row.get("student").get("studentCode").stringValue()).isEqualTo("STD-TEST-001");
        assertThat(row.get("student").get("fullName").stringValue()).isEqualTo("Alice Anders");
        assertThat(row.get("conversationId").stringValue()).isEqualTo(conversationA);
        assertThat(row.get("conversationTitle").stringValue()).isEqualTo("How should I prepare for a presentation?");
        // The question is the Student's message this answer replied to, not the first one of the conversation.
        assertThat(row.get("question").stringValue()).isEqualTo("Can you suggest a study technique?");
        assertThat(row.get("answer").stringValue()).isEqualTo("Try spaced repetition.");
        assertThat(row.get("submittedAt").stringValue()).isEqualTo("2054-11-10T05:00:00Z");
        // Long texts are previews in the list and complete in the detail.
        assertThat(page.get("content").get(2).get("answer").stringValue()).hasSize(301).endsWith("…");
        JsonNode detail = ok(get(FEEDBACK + "/" + positive));
        assertThat(detail.get("answer").stringValue()).hasSize(422).startsWith("Practise aloud twice.");
        assertThat(detail.get("model").stringValue()).isEqualTo("scripted-model");
        assertThat(detail.get("sources")).isEmpty();

        JsonNode totals = ok(get(FEEDBACK + "/statistics"));
        assertThat(totals.get("totalFeedback").asLong()).isEqualTo(3);
        assertThat(totals.get("positive").asLong()).isEqualTo(1);
        assertThat(totals.get("negative").asLong()).isEqualTo(2);
        assertThat(totals.get("awaitingReview").asLong()).isEqualTo(3);

        // Filters and search.
        assertThat(feedbackIds(ok(get(FEEDBACK).param("rating", "POSITIVE")))).containsExactly(positive);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("rating", "NEGATIVE").param("size", "1")))).containsExactly(third);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("search", "1,000 BAHT")))).containsExactly(negative);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("search", "library")))).containsExactly(third);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("search", "std-test-002")))).containsExactly(third);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("search", "alice")))).containsExactly(negative, positive);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("from", "2054-11-10T04:00:00Z").param("to", "2054-11-11T00:00:00Z"))))
                .containsExactly(negative);

        // Review: status, note, reviewer and time.
        advance(Duration.ofHours(1));
        JsonNode reviewed = ok(patch(FEEDBACK + "/" + negative + "/review"),
                Map.of("reviewStatus", "REVIEWED", "reviewNote", "  Handbook needs an update  "));
        assertThat(reviewed.get("reviewStatus").stringValue()).isEqualTo("REVIEWED");
        assertThat(reviewed.get("reviewNote").stringValue()).isEqualTo("Handbook needs an update");
        assertThat(reviewed.get("reviewedBy").get("id").asLong()).isEqualTo(adminId);
        assertThat(reviewed.get("reviewedBy").get("displayName").stringValue()).isEqualTo("Review Admin");
        assertThat(reviewed.get("reviewedAt").stringValue()).isEqualTo("2054-11-11T06:00:00Z");
        assertThat(reviewed.get("question").stringValue()).isEqualTo("Can you suggest a study technique?");
        ok(patch(FEEDBACK + "/" + third + "/review"), Map.of("reviewStatus", "RESOLVED"));
        assertThat(feedbackIds(ok(get(FEEDBACK).param("reviewStatus", "PENDING")))).containsExactly(positive);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("reviewStatus", "REVIEWED")))).containsExactly(negative);
        assertThat(feedbackIds(ok(get(FEEDBACK).param("reviewStatus", "RESOLVED").param("rating", "NEGATIVE")))).containsExactly(third);
        assertThat(ok(get(FEEDBACK + "/statistics")).get("awaitingReview").asLong()).isEqualTo(1);
        // Back to "awaiting review" clears the reviewer.
        JsonNode reopened = ok(patch(FEEDBACK + "/" + third + "/review"), Map.of("reviewStatus", "PENDING"));
        assertThat(reopened.get("reviewedBy").isNull()).isTrue();
        assertThat(reopened.get("reviewedAt").isNull()).isTrue();

        for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of(), Map.of("reviewStatus", "DONE"),
                Map.of("reviewStatus", "REVIEWED", "reviewNote", "x".repeat(1001)), Map.of("reviewStatus", "REVIEWED", "rating", "POSITIVE")))
            error(perform(patch(FEEDBACK + "/" + negative + "/review").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(bad)), admin), 400, "VALIDATION_ERROR");
        error(perform(patch(FEEDBACK + "/999999/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewStatus\":\"REVIEWED\"}"), admin), 404, "ASSISTANT_FEEDBACK_NOT_FOUND");
        error(perform(get(FEEDBACK + "/999999"), admin), 404, "ASSISTANT_FEEDBACK_NOT_FOUND");
        for (String[] bad : List.of(new String[] {"rating", "GOOD"}, new String[] {"reviewStatus", "DONE"}, new String[] {"size", "51"},
                new String[] {"from", "x"}))
            error(perform(get(FEEDBACK).param(bad[0], bad[1]), admin), 400, "VALIDATION_ERROR");
        assertThat(perform(patch(FEEDBACK + "/" + negative + "/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewStatus\":\"RESOLVED\"}"), tokenA).getStatus()).isEqualTo(403);

        MockHttpServletResponse export = perform(get(FEEDBACK + "/export").param("rating", "NEGATIVE"), admin);
        assertThat(export.getStatus()).isEqualTo(200);
        assertThat(export.getHeader("Content-Disposition")).contains("ai-feedback.csv");
        String[] lines = new String(export.getContentAsByteArray(), StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).startsWith("﻿\"No.\",\"Student Code\",\"Student Name\",\"Question\",\"AI Response\",\"Rating\",\"Comment\"");
        assertThat(lines[2]).startsWith("\"2\",\"STD-TEST-001\",\"Alice Anders\",\"Can you suggest a study technique?\","
                + "\"Try spaced repetition.\",\"NEGATIVE\",\"The handbook says 1,000 baht\",\"2054-11-10T05:00:00Z\",\"REVIEWED\","
                + "\"Handbook needs an update\",\"Review Admin\"");
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    private JsonNode chat(String token, String conversationId, String question, String answer) throws Exception {
        answers.add(answer);
        var body = new LinkedHashMap<String, Object>();
        body.put("message", question);
        if (conversationId != null) body.put("conversationId", conversationId);
        return ok(mvc.perform(post(CHAT).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andReturn().getResponse());
    }

    // One turn answered by the model in plain text; returns the conversationId.
    private String talk(String token, String conversationId, String question, String answer) throws Exception {
        return chat(token, conversationId, question, answer).get("conversationId").stringValue();
    }

    private MockHttpServletResponse rate(String token, String conversationId, Map<String, Object> body) throws Exception {
        return perform(put(STUDENT_CONVERSATIONS + "/" + conversationId + "/feedback").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)), token);
    }

    // Rates the newest answer of the conversation and returns the feedback id.
    private long rateLatest(String token, String conversationId, String rating, String comment) throws Exception {
        long answer = db.queryForObject("SELECT max(id) FROM assistant_messages WHERE conversation_id=?::uuid AND role='ASSISTANT'",
                Long.class, conversationId);
        var body = new LinkedHashMap<String, Object>();
        body.put("messageId", answer);
        body.put("rating", rating);
        if (comment != null) body.put("comment", comment);
        ok(rate(token, conversationId, body));
        return db.queryForObject("SELECT id FROM assistant_message_feedback WHERE message_id=?", Long.class, answer);
    }

    // Moves the clock and signs everyone in again: an access token is valid for 15 minutes.
    private void advance(Duration duration) throws Exception {
        clock.advance(duration);
        tokenA = studentLogin("STD-TEST-001");
        tokenB = studentLogin("STD-TEST-002");
        admin = adminLogin();
    }

    private long count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    private static List<String> ids(JsonNode page) {
        var ids = new ArrayList<String>();
        page.get("content").forEach(c -> ids.add(c.get("conversationId").stringValue()));
        return ids;
    }

    private static List<Long> feedbackIds(JsonNode page) {
        var ids = new ArrayList<Long>();
        page.get("content").forEach(c -> ids.add(c.get("id").asLong()));
        return ids;
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception { return ok(perform(request, admin)); }

    private JsonNode ok(MockHttpServletRequestBuilder request, Map<String, Object> body) throws Exception {
        return ok(perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)), admin));
    }

    private JsonNode ok(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsByteArray());
    }

    private void error(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(status);
        assertThat(json.readTree(response.getContentAsByteArray()).get("code").stringValue()).isEqualTo(code);
    }

    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", adminId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", adminId, code);
    }

    private String adminLogin() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "spec59.admin@example.test", "password", ADMIN_PASSWORD)))).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }

    private String studentLogin(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password",
                        AssistantAcademicFixture.PASSWORD)))).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
}
