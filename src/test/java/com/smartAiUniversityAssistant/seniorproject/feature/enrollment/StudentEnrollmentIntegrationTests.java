package com.smartAiUniversityAssistant.seniorproject.feature.enrollment;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.PublishedMeetingTestData;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Student self-enrollment from the mobile application (spec file 43). */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class StudentEnrollmentIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/student/enrollments";
    private static final String PASSWORD = "student enrollment password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    private long actorId, faculty, department, lecture, courseA, courseB, courseInactive, semester, otherSemester;
    private long sectionA1, sectionA2, sectionB1, sectionClosed, sectionInactiveCourse, sectionOtherSemester, sectionUnscheduled;
    private long studentA, studentB;
    private String hash;

    @BeforeEach
    void fixture() {
        clock.set(Instant.parse("2026-11-10T04:00:00Z"));
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        cleanDatabase();
        actorId = id("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('ENR-ACTOR','Enroll','Actor','enroll.actor@example.test','ACTIVE') RETURNING id
                """);
        hash = encoder.encode(PASSWORD);
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('ICT','College of ICT','ไอซีที',?) RETURNING id", actorId);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", faculty, actorId);
        lecture = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('จอห์น','Dr. John Smith',?,?,?) RETURNING id", faculty, department, actorId);
        courseB = course("CS302", "Operating Systems", true);
        courseA = course("CS301", "Data Structures", true);
        courseInactive = course("CS399", "Retired Course", false);
        semester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'ภาค 2','Semester 2',?) RETURNING id", actorId);
        otherSemester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2027,'ภาค 1','Semester 1',?) RETURNING id", actorId);
        sectionA1 = section(courseA, "SEC-01", semester, 2, "ACTIVE", "11-402");
        sectionA2 = section(courseA, "SEC-02", semester, 40, "ACTIVE", "11-403");
        sectionB1 = section(courseB, "SEC-01", semester, 1, "ACTIVE", "11-404");
        // No published meeting: not on the timetable, so it is neither listed nor enrollable (spec file 44).
        sectionUnscheduled = section(courseB, "SEC-02", semester, 40, "ACTIVE", null);
        sectionClosed = section(courseB, "SEC-09", semester, 40, "CLOSED", null);
        sectionInactiveCourse = section(courseInactive, "SEC-01", semester, 40, "ACTIVE", null);
        sectionOtherSemester = section(courseA, "SEC-03", otherSemester, 40, "ACTIVE", "11-405");
        studentA = student("6600001");
        studentB = student("6600002");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        // Leave nothing behind: published meetings would block other classes' course section and room cleanup.
        cleanDatabase();
    }

    @Test
    void availableSectionsListOnlyOpenSectionsOfTheStudentsSemester() throws Exception {
        String token = login("6600001");
        var rows = tree(get(token, "/available-sections").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn());
        assertThat(ids(rows)).containsExactly(sectionA1, sectionA2, sectionB1);
        JsonNode a1 = rows.get(0);
        assertThat(a1.get("courseCode").asText()).isEqualTo("CS301");
        assertThat(a1.get("capacity").asInt()).isEqualTo(2);
        assertThat(a1.get("seatsAvailable").asInt()).isEqualTo(2);
        assertThat(a1.get("canEnroll").asBoolean()).isTrue();
        assertThat(a1.get("blockedReason").isNull()).isTrue();
        assertThat(a1.get("enrollmentStatus").isNull()).isTrue();
        assertThat(a1.get("lecturerName").asText()).isEqualTo("Dr. John Smith");
        assertThat(a1.get("meetings").size()).isOne();
        // A Student without a semester has no verified current term, so nothing is offered.
        db.update("UPDATE students SET semester_id=NULL WHERE id=?", studentA);
        assertThat(tree(get(token, "/available-sections").andExpect(status().isOk()).andReturn()).size()).isZero();
    }

    @Test
    void enrollCreatesAPendingRequestForTheTokenStudentOnly() throws Exception {
        String token = login("6600001");
        var created = tree(enroll(token, sectionA1).andExpect(status().isCreated()).andReturn());
        assertThat(created.get("enrollmentStatus").asText()).isEqualTo("PENDING");
        assertThat(created.get("enrollmentDate").asText()).isEqualTo("2026-11-10");
        assertThat(created.get("courseSectionId").asLong()).isEqualTo(sectionA1);
        assertThat(created.get("description").asText()).isEqualTo("CS301 description");
        var row = db.queryForMap("SELECT student_id,status,created_by FROM enrollments WHERE id=?", created.get("enrollmentId").asLong());
        assertThat(row.get("student_id")).isEqualTo(studentA);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("created_by")).isNull();
        // Pending requests appear in the existing academic views and in the available list.
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/student/academic/enrollments?status=PENDING"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].courseSectionId").value(sectionA1));
        var rows = tree(get(token, "/available-sections").andReturn());
        assertThat(rows.get(0).get("enrollmentStatus").asText()).isEqualTo("PENDING");
        assertThat(rows.get(0).get("blockedReason").asText()).isEqualTo("ALREADY_ENROLLED");
        assertThat(rows.get(1).get("blockedReason").asText()).isEqualTo("ALREADY_ENROLLED_IN_COURSE");
        assertThat(rows.get(2).get("canEnroll").asBoolean()).isTrue();
    }

    @Test
    void enrollRejectsDuplicatesConflictsFullClosedAndOtherSemesterSections() throws Exception {
        String a = login("6600001");
        String b = login("6600002");
        enroll(a, sectionA1).andExpect(status().isCreated());
        enroll(a, sectionA1).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ENROLLMENT_ALREADY_EXISTS"));
        enroll(a, sectionA2).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("STUDENT_ALREADY_ENROLLED_IN_COURSE"));
        enroll(b, sectionB1).andExpect(status().isCreated());
        enroll(a, sectionB1).andExpect(status().isConflict()).andExpect(jsonPath("code").value("COURSE_SECTION_FULL"));
        enroll(a, sectionClosed).andExpect(status().isConflict()).andExpect(jsonPath("code").value("COURSE_SECTION_NOT_AVAILABLE"));
        enroll(a, sectionInactiveCourse).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_SECTION_NOT_AVAILABLE"));
        enroll(a, sectionOtherSemester).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_SECTION_NOT_AVAILABLE"));
        enroll(a, 999_999L).andExpect(status().isNotFound());
        mvc.perform(auth(post(BASE), a).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        var full = tree(get(a, "/available-sections").andReturn());
        assertThat(full.get(2).get("blockedReason").asText()).isEqualTo("SECTION_FULL");
        assertThat(full.get(2).get("full").asBoolean()).isTrue();
    }

    @Test
    void withdrawReleasesTheSeatAndAWithdrawnRequestCanBeMadeAgain() throws Exception {
        String a = login("6600001");
        String b = login("6600002");
        long enrollment = tree(enroll(a, sectionB1).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        // Another Student's enrollment is indistinguishable from a missing one.
        withdraw(b, enrollment).andExpect(status().isNotFound());
        withdraw(a, enrollment).andExpect(status().isOk()).andExpect(jsonPath("enrollmentStatus").value("WITHDRAWN"));
        withdraw(a, enrollment).andExpect(status().isOk()).andExpect(jsonPath("enrollmentStatus").value("WITHDRAWN"));
        enroll(b, sectionB1).andExpect(status().isCreated());
        withdraw(b, tree(get(b, "/available-sections").andReturn()).get(2).get("enrollmentId").asLong()).andExpect(status().isOk());
        // Re-requesting reuses the row: UNIQUE(student_id, course_section_id) keeps one lifecycle per pair.
        enroll(a, sectionB1).andExpect(status().isCreated()).andExpect(jsonPath("enrollmentId").value(enrollment))
                .andExpect(jsonPath("enrollmentStatus").value("PENDING"));
        assertThat(db.queryForObject("SELECT count(*) FROM enrollments WHERE student_id=?", Integer.class, studentA)).isOne();
    }

    @Test
    void activeEnrollmentsCanBeWithdrawnButDroppedOnesCannotBeReRequested() throws Exception {
        String a = login("6600001");
        long active = id("""
                INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,created_by)
                VALUES (?,?,DATE '2026-08-15','ACTIVE',?) RETURNING id
                """, studentA, sectionA1, actorId);
        long dropped = id("""
                INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,created_by)
                VALUES (?,?,DATE '2026-08-15','DROPPED',?) RETURNING id
                """, studentA, sectionB1, actorId);
        var rows = tree(get(a, "/available-sections").andReturn());
        assertThat(rows.get(2).get("blockedReason").asText()).isEqualTo("DROPPED_BY_UNIVERSITY");
        enroll(a, sectionB1).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ENROLLMENT_DROPPED_BY_UNIVERSITY"));
        withdraw(a, dropped).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("INVALID_ENROLLMENT_STATUS_TRANSITION"));
        withdraw(a, active).andExpect(status().isOk()).andExpect(jsonPath("enrollmentStatus").value("WITHDRAWN"));
        assertThat(db.queryForObject("SELECT created_by FROM enrollments WHERE id=?", Long.class, active)).isEqualTo(actorId);
    }

    @Test
    void lockActivatesEveryPendingRequestWithoutAnAdmin() throws Exception {
        String a = login("6600001");
        String b = login("6600002");
        lock(a).andExpect(status().isConflict()).andExpect(jsonPath("code").value("NO_PENDING_ENROLLMENTS"));
        long first = tree(enroll(a, sectionA1).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        long second = tree(enroll(a, sectionB1).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        enroll(b, sectionA2).andExpect(status().isCreated());
        // A pending request in another semester is not part of this lock.
        db.update("UPDATE students SET semester_id=? WHERE id=?", otherSemester, studentA);
        long later = tree(enroll(a, sectionOtherSemester).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        db.update("UPDATE students SET semester_id=? WHERE id=?", semester, studentA);

        var locked = tree(lock(a).andExpect(status().isOk()).andReturn());
        assertThat(locked.size()).isEqualTo(2);
        assertThat(locked.get(0).get("enrollmentStatus").asText()).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT status FROM enrollments WHERE id=?", String.class, first)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT status FROM enrollments WHERE id=?", String.class, second)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT status FROM enrollments WHERE id=?", String.class, later)).isEqualTo("PENDING");
        // Only the token student's requests are locked.
        assertThat(db.queryForObject("SELECT status FROM enrollments WHERE student_id=?", String.class, studentB)).isEqualTo("PENDING");
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/student/academic/courses"), a))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        // Locked courses can still be withdrawn; a second lock has nothing left.
        withdraw(a, first).andExpect(status().isOk()).andExpect(jsonPath("enrollmentStatus").value("WITHDRAWN"));
        lock(a).andExpect(status().isConflict()).andExpect(jsonPath("code").value("NO_PENDING_ENROLLMENTS"));
    }

    @Test
    void lockFailsAsAWholeWhenARequestedSectionClosed() throws Exception {
        String a = login("6600001");
        long first = tree(enroll(a, sectionA1).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        enroll(a, sectionB1).andExpect(status().isCreated());
        db.update("UPDATE course_sections SET status='CLOSED' WHERE id=?", sectionB1);
        lock(a).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ENROLLMENT_LOCK_SECTION_CLOSED"));
        assertThat(db.queryForObject("SELECT status FROM enrollments WHERE id=?", String.class, first)).isEqualTo("PENDING");
    }

    @Test
    void eachSubjectCanBeLockedOnItsOwn() throws Exception {
        String a = login("6600001");
        String b = login("6600002");
        long first = tree(enroll(a, sectionA1).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        long second = tree(enroll(a, sectionB1).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        // Another student's request is indistinguishable from a missing one.
        lockOne(b, first).andExpect(status().isNotFound());
        lockOne(a, first).andExpect(status().isOk()).andExpect(jsonPath("enrollmentStatus").value("ACTIVE"));
        assertThat(db.queryForObject("SELECT status FROM enrollments WHERE id=?", String.class, second)).isEqualTo("PENDING");
        // Locking again is harmless; a withdrawn request cannot be locked.
        lockOne(a, first).andExpect(status().isOk()).andExpect(jsonPath("enrollmentStatus").value("ACTIVE"));
        withdraw(a, second).andExpect(status().isOk());
        lockOne(a, second).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ENROLLMENT_NOT_PENDING"));
        // A section closed since the request cannot be locked.
        long b1 = tree(enroll(b, sectionA2).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        db.update("UPDATE course_sections SET status='CLOSED' WHERE id=?", sectionA2);
        lockOne(b, b1).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ENROLLMENT_LOCK_SECTION_CLOSED"));
    }

    @Test
    void onlySectionsOnThePublishedTimetableAreListedAndEnrollable() throws Exception {
        String a = login("6600001");
        assertThat(ids(tree(get(a, "/available-sections").andReturn()))).doesNotContain(sectionUnscheduled);
        enroll(a, sectionUnscheduled).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_SECTION_NOT_IN_TIMETABLE"));
        // A draft meeting is not a published timetable.
        db.update("UPDATE course_section_meetings SET status='DRAFT',is_active=FALSE WHERE course_section_id=?", sectionA2);
        assertThat(ids(tree(get(a, "/available-sections").andReturn()))).containsExactly(sectionA1, sectionB1);
        enroll(a, sectionA2).andExpect(status().isConflict()).andExpect(jsonPath("code").value("COURSE_SECTION_NOT_IN_TIMETABLE"));
        assertThat(db.queryForObject("SELECT count(*) FROM enrollments", Integer.class)).isZero();
    }

    @Test
    void enrollmentClosesAfterTheDueDate() throws Exception {
        String a = login("6600001");
        // Today is 2026-11-10 in Bangkok. The due date itself is still open.
        db.update("UPDATE course_section_meetings SET effective_from=DATE '2026-11-20',enrollment_due_date=DATE '2026-11-10' WHERE course_section_id=?", sectionA1);
        JsonNode open = tree(get(a, "/available-sections").andReturn()).get(0);
        assertThat(open.get("enrollmentDueDate").asText()).isEqualTo("2026-11-10");
        assertThat(open.get("enrollmentClosed").asBoolean()).isFalse();
        assertThat(open.get("canEnroll").asBoolean()).isTrue();
        // A section without a due date has no deadline.
        assertThat(tree(get(a, "/available-sections").andReturn()).get(1).get("enrollmentDueDate").isNull()).isTrue();
        long pending = tree(enroll(a, sectionA1).andExpect(status().isCreated()).andReturn()).get("enrollmentId").asLong();
        withdraw(a, pending).andExpect(status().isOk());

        db.update("UPDATE course_section_meetings SET enrollment_due_date=DATE '2026-11-09' WHERE course_section_id=?", sectionA1);
        JsonNode closed = tree(get(a, "/available-sections").andReturn()).get(0);
        assertThat(closed.get("enrollmentClosed").asBoolean()).isTrue();
        assertThat(closed.get("canEnroll").asBoolean()).isFalse();
        assertThat(closed.get("blockedReason").asText()).isEqualTo("ENROLLMENT_CLOSED");
        enroll(a, sectionA1).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ENROLLMENT_DUE_DATE_PASSED"));
        assertThat(db.queryForObject("SELECT status FROM enrollments WHERE id=?", String.class, pending)).isEqualTo("WITHDRAWN");
        // The earliest due date of the section's published meetings decides.
        db.update("UPDATE course_section_meetings SET enrollment_due_date=DATE '2026-11-15' WHERE course_section_id=?", sectionA1);
        db.update("""
                INSERT INTO course_section_meetings(course_section_id,meeting_type,day_of_week,start_time,end_time,effective_from,effective_to,
                    room_id,status,is_active,enrollment_due_date)
                SELECT course_section_id,'LAB',4,start_time,end_time,effective_from,effective_to,room_id,'PUBLISHED',TRUE,DATE '2026-11-08'
                FROM course_section_meetings WHERE course_section_id=?
                """, sectionA1);
        enroll(a, sectionA1).andExpect(status().isConflict()).andExpect(jsonPath("code").value("ENROLLMENT_DUE_DATE_PASSED"));
    }

    @Test
    void endpointsRequireAStudentToken() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/available-sections"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"courseSectionId\":" + sectionA1 + "}"))
                .andExpect(status().isUnauthorized());
    }

    private void cleanDatabase() {
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
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
    private long course(String code, String name, boolean active) {
        return id("""
                INSERT INTO courses(course_code,course_name,credit_hours,description,faculty_id,department_id,course_type,is_active,created_by)
                VALUES (?,?,3,?,?,?,300,?,?) RETURNING id
                """, code, name, code + " description", faculty, department, active, actorId);
    }
    private long section(long course, String number, long semester, int capacity, String status, String room) {
        long id = id("""
                INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,semester_id,status,created_by)
                VALUES (?,?,?,?,?,?,?) RETURNING id
                """, course, number, capacity, lecture, semester, status, actorId);
        PublishedMeetingTestData.publish(db, id, room, "Tuesday 09:00-11:45");
        return id;
    }
    private long student(String code) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status,semester_id)
                VALUES (?,?,'First','Last','ACTIVE',?) RETURNING id
                """, code, code + "@rsu.ac.th", semester);
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, hash);
        return id;
    }
    private String login(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private ResultActions get(String token, String path) throws Exception {
        return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + path), token));
    }
    private ResultActions enroll(String token, long section) throws Exception {
        return mvc.perform(auth(post(BASE), token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseSectionId\":" + section + "}"));
    }
    private ResultActions withdraw(String token, long enrollment) throws Exception {
        return mvc.perform(auth(post(BASE + "/" + enrollment + "/withdraw"), token));
    }
    private ResultActions lockOne(String token, long enrollment) throws Exception {
        return mvc.perform(auth(post(BASE + "/" + enrollment + "/lock"), token));
    }
    private ResultActions lock(String token) throws Exception {
        return mvc.perform(auth(post(BASE + "/lock"), token));
    }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
    private static List<Long> ids(JsonNode rows) {
        var ids = new ArrayList<Long>();
        rows.forEach(r -> ids.add(r.get("courseSectionId").asLong()));
        return ids;
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
