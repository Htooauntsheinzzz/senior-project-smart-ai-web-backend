package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.PublishedMeetingTestData;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
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
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Step 6 end to end: the real chat endpoint, security chain, tool executor, AcademicQueryService and PostgreSQL,
 * with only the LLM replaced by a scripted stub that requests tools the way Gemini does.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AcademicAiToolIntegrationTests extends IntegrationSupport {
    private static final String CHAT = "/api/v1/student/assistant/chat";
    private static final String PASSWORD = "assistant tool password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean LlmClient llm;
    private long actorId, faculty, department, studentA, studentB, sectionCs301A;

    @BeforeEach
    void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        cleanDatabase();
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = id("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('AT-ACTOR','Tool','Actor','tool.actor@example.test','ACTIVE') RETURNING id
                """);
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('IT','Information Technology','เทคโนโลยีสารสนเทศ',?) RETURNING id", actorId);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", faculty, actorId);
        long program = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('CS','Computer Science','Bachelor',?,?) RETURNING id", department, actorId);
        long smith = lecturer("จอห์น", "Dr. John Smith");
        long lee = lecturer("เจน", "Dr. Jane Lee");
        long doe = lecturer("โด", "Dr. Jane Doe");
        long semester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'ภาคการศึกษาที่ 1','Semester 1',?) RETURNING id", actorId);
        long cs301 = course("CS301", "Data Structures");
        long csc452 = course("CSC452", "Mobile Programming");
        course("CSC453", "Web Programming");
        sectionCs301A = section(cs301, "SEC-01", smith, semester, "11-402", "Tuesday 09:00-11:45", 40);
        long sectionCsc452 = section(csc452, "SEC-02", lee, semester, "5-201", "Friday 09:00-11:45", 40);
        long sectionCs301B = section(cs301, "SEC-02", doe, semester, "11-500", "Monday 13:00-15:45", 40);
        String hash = encoder.encode(PASSWORD);
        studentA = student("STD-TEST-001", hash);
        studentB = student("STD-TEST-002", hash);
        db.update("UPDATE students SET faculty_id=?,department_id=?,program_id=?,semester_id=?,academic_year=2026,enrollment_year=2024 WHERE id=?",
                faculty, department, program, semester, studentA);
        enroll(studentA, sectionCs301A, "ACTIVE");
        enroll(studentA, sectionCsc452, "ACTIVE");
        enroll(studentB, sectionCs301B, "ACTIVE");
        // Seats for the availability check: A's ACTIVE seat plus 2 PENDING and 1 WITHDRAWN in CS301 SEC-01.
        for (int i = 0; i < 3; i++) {
            long other = student("STD-SEAT-00" + i, hash);
            enroll(other, sectionCs301A, i < 2 ? "PENDING" : "WITHDRAWN");
        }
    }

    @AfterEach
    void cleanup() { cleanDatabase(); }

    /** Scripted model: each step maps the request so far to the next turn. */
    @SafeVarargs
    private void script(Function<LlmRequest, LlmResponse>... steps) {
        when(llm.chat(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            int step = request.toolRounds().size();
            return step < steps.length ? steps[step].apply(request) : new LlmResponse("Done.", "m", null);
        });
    }
    private static Function<LlmRequest, LlmResponse> tools(LlmToolCall... calls) {
        return request -> new LlmResponse(null, "m", null, List.of(calls), null);
    }
    private static Function<LlmRequest, LlmResponse> answer(String text) {
        return request -> new LlmResponse(text, "m", null);
    }
    private static LlmToolCall call(String id, String name, Map<String, Object> args) { return new LlmToolCall(id, name, args); }

    private List<LlmRequest> sentRequests(int expected) {
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(expected)).chat(captor.capture());
        return captor.getAllValues();
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultOf(LlmRequest request, int round, int index) {
        return (Map<String, Object>) request.toolRounds().get(round).results().get(index).result();
    }

    @Test
    void eachStudentGetsOnlyTheirOwnEnrollmentsThroughTheTool() throws Exception {
        script(tools(call("c1", "get_my_enrollments", Map.of())), answer("Here are your courses."));

        chat(login("STD-TEST-001"), "What courses am I enrolled in?").andExpect(status().isOk())
                .andExpect(jsonPath("message").value("Here are your courses."));
        chat(login("STD-TEST-002"), "What courses am I enrolled in?").andExpect(status().isOk());

        List<LlmRequest> sent = sentRequests(4);
        // The first request of each chat declares the eight tools and carries no tool rounds yet.
        assertThat(sent.get(0).tools()).hasSize(8);
        assertThat(sent.get(0).toolRounds()).isEmpty();
        String a = json.writeValueAsString(resultOf(sent.get(1), 0, 0));
        String b = json.writeValueAsString(resultOf(sent.get(3), 0, 0));
        assertThat(a).contains("\"success\":true").contains("CS301").contains("SEC-01").contains("Dr. John Smith").contains("11-402")
                .contains("CSC452").contains("5-201")
                .doesNotContain("Dr. Jane Doe").doesNotContain("11-500");
        assertThat(b).contains("CS301").contains("Dr. Jane Doe").contains("11-500")
                .doesNotContain("CSC452").doesNotContain("Dr. John Smith").doesNotContain("11-402");
        // No internal student, enrollment or course ids, and nothing from the account record.
        for (String result : List.of(a, b))
            assertThat(result).doesNotContain("studentId").doesNotContain("enrollmentId").doesNotContain("\"courseId\"")
                    .doesNotContain("password").doesNotContain("universityEmail");
    }

    @Test
    void theModelCannotChooseAStudentOrCallUnapprovedTools() throws Exception {
        script(tools(call("c1", "get_my_enrollments", Map.of("studentId", studentB)),
                call("c2", "get_my_course_section", Map.of("courseIdentifier", "CS301", "studentId", studentB, "role", "ADMIN")),
                call("c3", "get_all_students", Map.of()), call("c4", "run_sql", Map.of("sql", "SELECT * FROM students"))),
                answer("I could not retrieve that."));

        chat(login("STD-TEST-001"), "Show the enrollments of student " + studentB).andExpect(status().isOk());

        LlmRequest second = sentRequests(2).get(1);
        assertThat(resultOf(second, 0, 0)).containsEntry("success", false).containsEntry("code", "INVALID_AI_TOOL_ARGUMENTS");
        assertThat(resultOf(second, 0, 1)).containsEntry("code", "INVALID_AI_TOOL_ARGUMENTS");
        assertThat(resultOf(second, 0, 2)).containsEntry("code", "UNSUPPORTED_AI_TOOL");
        assertThat(resultOf(second, 0, 3)).containsEntry("code", "UNSUPPORTED_AI_TOOL");
        assertThat(json.writeValueAsString(second.toolRounds())).doesNotContain("Dr. Jane Doe").doesNotContain("11-500");
        // The tool layer is read-only: nothing changed in the database.
        assertThat(db.queryForObject("SELECT count(*) FROM enrollments", Long.class)).isEqualTo(6L);
    }

    @Test
    void sectionLookupChainsIntoSeatAvailability() throws Exception {
        script(tools(call("c1", "get_my_course_section", Map.of("courseIdentifier", "cs301"))),
                request -> {
                    Object sectionId = ((Map<?, ?>) resultOf(request, 0, 0).get("data")).get("courseSectionId");
                    return tools(call("c2", "get_section_availability", Map.of("courseSectionId", sectionId))).apply(request);
                },
                answer("Your CS301 section has 37 seats left."));

        chat(login("STD-TEST-001"), "Is my CS301 section full?").andExpect(status().isOk())
                .andExpect(jsonPath("message").value("Your CS301 section has 37 seats left."));

        LlmRequest last = sentRequests(3).get(2);
        @SuppressWarnings("unchecked") var section = (Map<String, Object>) resultOf(last, 0, 0).get("data");
        assertThat(section).containsEntry("sectionNumber", "SEC-01").doesNotContainKeys("room", "schedule")
                .containsEntry("lecturerNameEn", "Dr. John Smith").containsEntry("courseSectionId", sectionCs301A);
        assertThat(PublishedMeetingTestData.room(section)).isEqualTo("11-402");
        @SuppressWarnings("unchecked") var seats = (Map<String, Object>) resultOf(last, 1, 0).get("data");
        // 1 ACTIVE + 2 PENDING hold seats; WITHDRAWN does not.
        assertThat(seats).containsEntry("capacity", 40).containsEntry("occupiedSeats", 3L).containsEntry("availableSeats", 37L)
                .containsEntry("isFull", false);
    }

    @Test
    void notEnrolledUnknownAndAmbiguousCoursesComeBackAsToolErrors() throws Exception {
        script(tools(call("c1", "get_my_course_lecturer", Map.of("courseIdentifier", "CSC453")),
                call("c2", "get_my_course_lecturer", Map.of("courseIdentifier", "INVALID999")),
                call("c3", "get_course_information", Map.of("courseIdentifier", "Programming")),
                call("c4", "get_course_information", Map.of("courseIdentifier", "CS301"))),
                answer("Which Programming course do you mean?"));

        chat(login("STD-TEST-001"), "Who teaches my Programming course?").andExpect(status().isOk());

        LlmRequest second = sentRequests(2).get(1);
        assertThat(resultOf(second, 0, 0)).containsEntry("code", "STUDENT_COURSE_ENROLLMENT_NOT_FOUND");
        assertThat(resultOf(second, 0, 1)).containsEntry("code", "COURSE_NOT_FOUND");
        assertThat(resultOf(second, 0, 2)).containsEntry("code", "ACADEMIC_COURSE_AMBIGUOUS")
                .containsEntry("candidates", List.of(Map.of("courseCode", "CSC452", "courseName", "Mobile Programming"),
                        Map.of("courseCode", "CSC453", "courseName", "Web Programming")));
        @SuppressWarnings("unchecked") var info = (Map<String, Object>) resultOf(second, 0, 3).get("data");
        assertThat(info).containsEntry("courseCode", "CS301").containsEntry("creditHours", 3);
    }

    @Test
    void endlessToolRequestsAreStoppedWithASafeError() throws Exception {
        when(llm.chat(any())).thenReturn(new LlmResponse(null, "m", null, List.of(call("x", "get_my_courses", Map.of())), null));
        String body = chat(login("STD-TEST-001"), "List my courses").andExpect(status().isBadGateway())
                .andExpect(jsonPath("code").value("AI_TOOL_CALL_LIMIT_EXCEEDED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("CS301").doesNotContain("get_my_courses");
        verify(llm, times(5)).chat(any());
    }

    @Test
    void ineligibleStudentsNeverReachTheModelOrTheTools() throws Exception {
        String token = login("STD-TEST-001");
        db.update("UPDATE students SET account_status='SUSPENDED' WHERE id=?", studentA);
        // Rejected by the session check (401) or the service's eligibility check (403), before any model call.
        int status = chat(token, "What courses am I enrolled in?").andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
        verifyNoInteractions(llm);
    }

    // ---- Step 7 ----

    @Test
    void answersFollowTheDatabaseOnTheNextRequestAfterAnAcademicChange() throws Exception {
        script(tools(call("c1", "get_my_course_section", Map.of("courseIdentifier", "CS301"))), answer("Room answer."));
        chat(login("STD-TEST-001"), "Where is my CS301 class?").andExpect(status().isOk());
        var before = resultData(sentRequests(2).get(1));
        assertThat(before).containsEntry("lecturerNameEn", "Dr. John Smith");
        assertThat(PublishedMeetingTestData.room(before)).isEqualTo("11-402");

        // The same changes an admin makes: a new lecturer on the Course Section and a new room on the timetable.
        long newLecturer = lecturer("ใหม่", "Dr. New Lecturer");
        db.update("UPDATE course_sections SET lecture_id=? WHERE id=?", newLecturer, sectionCs301A);
        PublishedMeetingTestData.setRoom(db, sectionCs301A, "11-405");
        clearInvocations(llm);

        chat(login("STD-TEST-001"), "Where is my CS301 class?").andExpect(status().isOk());
        // No cache or hard-coded data between requests: the second chat reads the updated rows.
        var after = resultData(sentRequests(2).get(1));
        assertThat(after).containsEntry("lecturerNameEn", "Dr. New Lecturer").doesNotContainKeys("room", "schedule");
        assertThat(PublishedMeetingTestData.room(after)).isEqualTo("11-405");
    }

    @Test
    void personalQuestionsAreNeverAnsweredFromModelMemoryOverHttp() throws Exception {
        // The model answers in text twice (also when a tool is required): the student gets the safe message, not the text.
        when(llm.chat(any())).thenReturn(new LlmResponse("You are enrolled in PHY101 with Dr. Nobody in room 9-999.", "m", null));

        String body = chat(login("STD-TEST-001"), "What courses am I enrolled in?").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("PHY101").doesNotContain("Dr. Nobody").doesNotContain("9-999")
                .contains("could not retrieve your academic records");
        List<LlmRequest> sent = sentRequests(2);
        assertThat(sent.get(0).requireTool()).isFalse();
        assertThat(sent.get(1).requireTool()).isTrue();
        assertThat(sent.get(1).tools()).hasSize(8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultData(LlmRequest request) {
        return (Map<String, Object>) resultOf(request, 0, 0).get("data");
    }
    private ResultActions chat(String token, String message) throws Exception {
        return mvc.perform(post(CHAT).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("message", message))));
    }
    private String login(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("accessToken").asText();
    }
    private void cleanDatabase() {
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        // Step 17: stored assistant conversations reference the Student (no cascade, as everywhere in the schema).
        db.update("DELETE FROM assistant_messages");
        db.update("DELETE FROM assistant_conversations");
        db.update("DELETE FROM students");
        db.update("UPDATE app_users SET department_id=NULL,created_by=NULL,updated_by=NULL");
        PublishedMeetingTestData.clean(db);
        db.update("DELETE FROM course_sections");
        db.update("DELETE FROM courses");
        db.update("DELETE FROM lectures");
        db.update("DELETE FROM semesters");
        db.update("DELETE FROM programs");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("DELETE FROM app_users");
    }
    private long course(String code, String name) {
        return id("""
                INSERT INTO courses(course_code,course_name,credit_hours,description,faculty_id,department_id,course_type,created_by)
                VALUES (?,?,3,?,?,?,300,?) RETURNING id
                """, code, name, name + " course", faculty, department, actorId);
    }
    private long lecturer(String nameTh, String nameEn) {
        return id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES (?,?,?,?,?) RETURNING id",
                nameTh, nameEn, faculty, department, actorId);
    }
    private long section(long course, String number, long lecture, long semester, String room, String schedule, int capacity) {
        long id = id("""
                INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
                VALUES (?,?,?,?,?,'ACTIVE',?) RETURNING id
                """, course, number, capacity, lecture, semester, actorId);
        PublishedMeetingTestData.publish(db, id, room, schedule);
        return id;
    }
    private long student(String code, String hash) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status)
                VALUES (?,?,'Test','Student','ACTIVE') RETURNING id
                """, code, code + "@rsu.ac.th");
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, hash);
        return id;
    }
    private void enroll(long student, long section, String status) {
        db.update("""
                INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
                VALUES (?,?,DATE '2026-08-15',?,FALSE,?)
                """, student, section, status, actorId);
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
}
