package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.InvalidEnrollmentStatusException;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.repository.EnrollmentRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.StudentAcademicService;
import jakarta.persistence.EntityManagerFactory;
import java.time.*;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class StudentAcademicIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/student/academic";
    private static final String ADMIN_PASSWORD = "academic actor password";
    private static final String PASSWORD = "student academic password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired StudentAcademicService academics;
    @Autowired EntityManagerFactory entityManagerFactory;
    @MockitoSpyBean EnrollmentRepository enrollmentRepository;
    private long actorId, faculty, department, program, lectureA, lectureB, courseA, courseB, semester1, semester2;
    private long sectionA1, sectionB1, sectionA2Next;
    private long studentA, studentB;
    private String hash;

    @BeforeEach
    void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        cleanDatabase();
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = id("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('ACA-ACTOR','Academic','Actor','academic.actor@example.test','ACTIVE') RETURNING id
                """);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='SUPER_ADMIN'", actorId);
        hash = encoder.encode(PASSWORD);
        faculty = id("INSERT INTO faculties(faculty_code,faculty_name_en,faculty_name_th,created_by) VALUES ('ICT','College of ICT','วิทยาลัยไอซีที',?) RETURNING id", actorId);
        department = id("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('CS','Computer Science',?,?) RETURNING id", faculty, actorId);
        program = id("INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES ('BSC-CS','BSc Computer Science','Bachelor',?,?) RETURNING id", department, actorId);
        lectureA = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,lecture_nickname,faculty_id,department_id,created_by) VALUES ('จอห์น','Dr. John Smith','John',?,?,?) RETURNING id", faculty, department, actorId);
        lectureB = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('นิตยา','Nittaya Chindakharn',?,?,?) RETURNING id", faculty, department, actorId);
        // Rows are inserted in reverse of the expected order (year desc, then course code), so ids never decide it.
        courseB = course("CS302", "Operating Systems", 200);
        courseA = course("CS301", "Data Structures & Algorithms", 300);
        semester2 = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2027,'ภาคการศึกษาที่ 1','Semester 1',?) RETURNING id", actorId);
        semester1 = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,'ภาคการศึกษาที่ 1','Semester 1',?) RETURNING id", actorId);
        sectionB1 = section(courseB, "SEC-01", lectureB, semester1, "IT-101", "Mon 13:00-16:00");
        sectionA1 = section(courseA, "SEC-01", lectureA, semester1, "11-402", "Tuesday 09:00-11:45");
        sectionA2Next = section(courseA, "SEC-02", lectureA, semester2, null, null);
        studentA = student("6600001", "Thanapon", "Srisuk", "ACTIVE", true);
        studentB = student("6600002", "Malee", "Chaiyo", "ACTIVE", false);
        db.update("UPDATE students SET faculty_id=?,department_id=?,program_id=?,semester_id=?,academic_year=2026,enrollment_year=2024,phone_number='0812345678',date_of_birth='2004-05-12' WHERE id=?",
                faculty, department, program, semester1, studentA);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        reset(enrollmentRepository);
        // Other feature tests delete faculties and app_users, which students reference.
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
    }

    @Test
    void profileReturnsTheTokenStudentsAcademicIdentity() throws Exception {
        String token = login("6600001");
        get(token, "/profile").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("studentId").value(studentA))
                .andExpect(jsonPath("studentCode").value("6600001"))
                .andExpect(jsonPath("fullName").value("Thanapon Srisuk"))
                .andExpect(jsonPath("universityEmail").value("6600001@rsu.ac.th"))
                .andExpect(jsonPath("phoneNumber").value("0812345678"))
                .andExpect(jsonPath("dateOfBirth").value("2004-05-12"))
                .andExpect(jsonPath("faculty.id").value(faculty))
                .andExpect(jsonPath("faculty.code").value("ICT"))
                .andExpect(jsonPath("faculty.nameEn").value("College of ICT"))
                .andExpect(jsonPath("faculty.nameTh").value("วิทยาลัยไอซีที"))
                .andExpect(jsonPath("department.code").value("CS"))
                .andExpect(jsonPath("department.nameEn").value("Computer Science"))
                .andExpect(jsonPath("department.nameTh").isEmpty())
                .andExpect(jsonPath("program.code").value("BSC-CS"))
                .andExpect(jsonPath("program.nameEn").value("BSc Computer Science"))
                .andExpect(jsonPath("semester.id").value(semester1))
                .andExpect(jsonPath("semester.academicYear").value(2026))
                .andExpect(jsonPath("semester.semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("semester.semesterNameTh").value("ภาคการศึกษาที่ 1"))
                .andExpect(jsonPath("academicYear").value(2026))
                .andExpect(jsonPath("enrollmentYear").value(2024))
                .andExpect(jsonPath("accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andExpect(jsonPath("failedLoginAttempts").doesNotExist())
                .andExpect(jsonPath("lockedUntil").doesNotExist())
                .andExpect(jsonPath("forcePasswordChange").doesNotExist())
                .andExpect(jsonPath("refreshToken").doesNotExist())
                .andExpect(jsonPath("sessionId").doesNotExist());
        // A client-supplied student id is never honoured: the token decides whose data is returned.
        get(token, "/profile?studentId=" + studentB).andExpect(status().isOk())
                .andExpect(jsonPath("studentId").value(studentA));
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + "/profile/" + studentB), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void selfRegisteredStudentWithoutPlacementGetsNullsAndEmptyLists() throws Exception {
        String token = login("6600002");
        get(token, "/profile").andExpect(status().isOk())
                .andExpect(jsonPath("studentId").value(studentB))
                .andExpect(jsonPath("faculty").isEmpty())
                .andExpect(jsonPath("department").isEmpty())
                .andExpect(jsonPath("program").isEmpty())
                .andExpect(jsonPath("semester").isEmpty())
                .andExpect(jsonPath("academicYear").isEmpty())
                .andExpect(jsonPath("enrollmentYear").isEmpty());
        for (String path : List.of("/enrollments", "/courses", "/schedule", "/lecturers", "/enrollments?status=ALL"))
            get(token, path).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void enrollmentsDefaultToActiveAndSupportEveryFilter() throws Exception {
        long pending = enroll(studentA, sectionB1, "PENDING", false);
        long active = enroll(studentA, sectionA1, "ACTIVE", false);
        long withdrawn = enroll(studentA, sectionA2Next, "WITHDRAWN", false);
        long softDeleted = enroll(studentA, section(courseB, "SEC-02", lectureB, semester2, null, null), "DROPPED", true);
        long otherStudents = enroll(studentB, sectionA1, "ACTIVE", false);
        String token = login("6600001");

        get(token, "/enrollments").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].enrollmentId").value(active))
                .andExpect(jsonPath("$[0].enrollmentDate").value("2026-08-15"))
                .andExpect(jsonPath("$[0].enrollmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].courseId").value(courseA))
                .andExpect(jsonPath("$[0].courseCode").value("CS301"))
                .andExpect(jsonPath("$[0].courseName").value("Data Structures & Algorithms"))
                .andExpect(jsonPath("$[0].creditHours").value(3))
                .andExpect(jsonPath("$[0].courseType").value(300))
                .andExpect(jsonPath("$[0].courseSectionId").value(sectionA1))
                .andExpect(jsonPath("$[0].sectionNumber").value("SEC-01"))
                .andExpect(jsonPath("$[0].semesterId").value(semester1))
                .andExpect(jsonPath("$[0].semesterAcademicYear").value(2026))
                .andExpect(jsonPath("$[0].semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("$[0].semesterNameTh").value("ภาคการศึกษาที่ 1"))
                .andExpect(jsonPath("$[0].lecturerId").value(lectureA))
                .andExpect(jsonPath("$[0].lecturerNameEn").value("Dr. John Smith"))
                .andExpect(jsonPath("$[0].room").value("11-402"))
                .andExpect(jsonPath("$[0].schedule").value("Tuesday 09:00-11:45"));
        assertThat(ids(get(token, "/enrollments?status=pending"))).containsExactly(pending);
        assertThat(ids(get(token, "/enrollments?status= Withdrawn "))).containsExactly(withdrawn);
        // The Semester comes from the Course Section, not from the Student's own semester (semester1).
        get(token, "/enrollments?status=WITHDRAWN")
                .andExpect(jsonPath("$[0].semesterId").value(semester2))
                .andExpect(jsonPath("$[0].semesterAcademicYear").value(2027));
        assertThat(ids(get(token, "/enrollments?status=DROPPED"))).isEmpty();
        // ALL: newest semester first, then course code; soft-deleted and other students' rows never appear.
        assertThat(ids(get(token, "/enrollments?status=all"))).containsExactly(withdrawn, active, pending)
                .doesNotContain(softDeleted, otherStudents);
        get(token, "/enrollments?status=APPROVED").andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ENROLLMENT_STATUS"));
        // An empty parameter falls back to the ACTIVE default, like an omitted one.
        assertThat(ids(get(token, "/enrollments?status="))).containsExactly(active);
    }

    @Test
    void coursesScheduleAndLecturersDeriveFromActiveEnrollments() throws Exception {
        long other = enroll(studentA, sectionB1, "ACTIVE", false);
        long active = enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentA, sectionA2Next, "DROPPED", false);
        enroll(studentB, sectionA1, "ACTIVE", false);
        String token = login("6600001");

        get(token, "/courses").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courseId").value(courseA))
                .andExpect(jsonPath("$[0].courseCode").value("CS301"))
                .andExpect(jsonPath("$[0].creditHours").value(3))
                .andExpect(jsonPath("$[0].description").value("CS301 description"))
                .andExpect(jsonPath("$[0].courseType").value(300))
                .andExpect(jsonPath("$[0].courseTypeName").value("Major Required"))
                .andExpect(jsonPath("$[0].courseSectionId").value(sectionA1))
                .andExpect(jsonPath("$[0].sectionNumber").value("SEC-01"))
                .andExpect(jsonPath("$[0].semesterId").value(semester1))
                .andExpect(jsonPath("$[0].enrollmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[1].courseCode").value("CS302"))
                .andExpect(jsonPath("$[1].courseTypeName").value("Major Elective"));
        get(token, "/courses?status=DROPPED").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courseSectionId").value(sectionA2Next))
                .andExpect(jsonPath("$[0].semesterId").value(semester2))
                .andExpect(jsonPath("$[0].semesterAcademicYear").value(2027));
        get(token, "/schedule?status=DROPPED").andExpect(jsonPath("$[0].semesterId").value(semester2));
        get(token, "/lecturers?status=DROPPED").andExpect(jsonPath("$[0].semesterId").value(semester2));

        get(token, "/schedule").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].enrollmentId").value(active))
                .andExpect(jsonPath("$[0].schedule").value("Tuesday 09:00-11:45"))
                .andExpect(jsonPath("$[0].room").value("11-402"))
                .andExpect(jsonPath("$[0].lecturerId").value(lectureA))
                .andExpect(jsonPath("$[0].lecturerNameEn").value("Dr. John Smith"))
                .andExpect(jsonPath("$[0].semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("$[0].enrollmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[1].enrollmentId").value(other))
                .andExpect(jsonPath("$[1].schedule").value("Mon 13:00-16:00"));

        get(token, "/lecturers").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lecturerId").value(lectureA))
                .andExpect(jsonPath("$[0].lecturerNameEn").value("Dr. John Smith"))
                .andExpect(jsonPath("$[0].lecturerNameTh").value("จอห์น"))
                .andExpect(jsonPath("$[0].lecturerNickname").value("John"))
                .andExpect(jsonPath("$[0].courseCode").value("CS301"))
                .andExpect(jsonPath("$[0].courseSectionId").value(sectionA1))
                .andExpect(jsonPath("$[0].semesterId").value(semester1))
                .andExpect(jsonPath("$[1].lecturerId").value(lectureB))
                .andExpect(jsonPath("$[1].lecturerNickname").isEmpty())
                .andExpect(jsonPath("$[1].courseCode").value("CS302"));
    }

    @Test
    void lecturerTeachingTwoSectionsAppearsOncePerSection() throws Exception {
        long courseC = course("CS401", "Compilers", 300);
        long sectionC1 = section(courseC, "SEC-01", lectureA, semester1, "11-501", "Thu 09:00-12:00");
        enroll(studentA, sectionC1, "ACTIVE", false);
        enroll(studentA, sectionA1, "ACTIVE", false);
        enroll(studentA, sectionB1, "ACTIVE", false);
        String token = login("6600001");

        get(token, "/lecturers").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].lecturerId").value(lectureA))
                .andExpect(jsonPath("$[0].courseCode").value("CS301"))
                .andExpect(jsonPath("$[0].courseSectionId").value(sectionA1))
                .andExpect(jsonPath("$[1].lecturerId").value(lectureB))
                .andExpect(jsonPath("$[1].courseCode").value("CS302"))
                .andExpect(jsonPath("$[2].lecturerId").value(lectureA))
                .andExpect(jsonPath("$[2].courseCode").value("CS401"))
                .andExpect(jsonPath("$[2].courseSectionId").value(sectionC1));
    }

    @Test
    void routesRequireAStudentTokenWithFullAccess() throws Exception {
        for (String path : List.of("/profile", "/enrollments", "/courses", "/schedule", "/lecturers")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + path))
                    .andExpect(status().isUnauthorized());
            get("not-a-jwt", path).andExpect(status().isUnauthorized());
        }
        // Admins never reach student academic routes.
        get(adminLogin(), "/profile").andExpect(status().isForbidden());

        // An expired access token is rejected before any query runs (15 minute TTL plus 30 second skew).
        String expiring = login("6600002");
        Instant issued = clock.instant();
        clock.set(issued.plus(Duration.ofMinutes(16)));
        for (String path : List.of("/profile", "/courses", "/schedule"))
            get(expiring, path).andExpect(status().isUnauthorized());
        clock.set(issued);

        // Forced password change keeps the existing /me, change-password, logout-only policy.
        db.update("UPDATE student_credentials SET force_password_change=TRUE WHERE student_id=?", studentA);
        String restricted = login("6600001");
        get(restricted, "/profile").andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        get(restricted, "/schedule").andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));

        // Deleting the account revokes its sessions, so the existing token stops authenticating.
        String token = login("6600002");
        get(token, "/profile").andExpect(status().isOk());
        mvc.perform(auth(delete("/api/v1/admin/students/" + studentB), adminLogin()))
                .andExpect(status().is2xxSuccessful());
        get(token, "/profile").andExpect(status().isUnauthorized());
    }

    @Test
    void queryFailuresNeverLeakDatabaseOrCodeDetails() throws Exception {
        enroll(studentA, sectionA1, "ACTIVE", false);
        String token = login("6600001");
        doThrow(new org.springframework.dao.InvalidDataAccessResourceUsageException(
                "could not execute statement [ERROR: relation \"enrollments\" does not exist] "
                        + "[select e1_0.id from enrollments e1_0] host=postgres:5432"))
                .when(enrollmentRepository).findStudentAcademicEnrollments(anyLong(), any());
        String unavailable = get(token, "/courses").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("code").value("AUTH_SERVICE_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        doThrow(new IllegalStateException("boom in EnrollmentRepository.findStudentAcademicEnrollments"))
                .when(enrollmentRepository).findStudentAcademicEnrollments(anyLong(), any());
        String unexpected = get(token, "/schedule").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();
        for (String body : List.of(unavailable, unexpected))
            assertThat(body.toLowerCase(Locale.ROOT)).doesNotContain("select", "enrollments e1_0", "relation", "postgres",
                    "hibernate", "repository", "exception", "\tat ", "boom", token.toLowerCase(Locale.ROOT));
    }

    @Test
    void serviceRejectsStudentsThatMayNotReadAcademicData() {
        for (String state : List.of("INACTIVE", "SUSPENDED", "PENDING")) {
            db.update("UPDATE students SET account_status=? WHERE id=?", state, studentB);
            assertThatThrownBy(() -> academics.getProfile(studentB)).isInstanceOf(StudentAccountNotActiveException.class);
            assertThatThrownBy(() -> academics.getEnrollments(studentB, "ACTIVE"))
                    .isInstanceOf(StudentAccountNotActiveException.class);
        }
        db.update("UPDATE students SET account_status='ACTIVE',is_deleted=TRUE WHERE id=?", studentB);
        assertThatThrownBy(() -> academics.getProfile(studentB)).isInstanceOf(StudentNotFoundException.class);
        assertThatThrownBy(() -> academics.getSchedule(-1, "ACTIVE")).isInstanceOf(StudentNotFoundException.class);
        db.update("UPDATE students SET is_deleted=FALSE WHERE id=?", studentB);
        db.update("DELETE FROM student_credentials WHERE student_id=?", studentB);
        assertThatThrownBy(() -> academics.getLecturers(studentB, "ACTIVE"))
                .isInstanceOf(InvalidStudentAccountStateException.class);
        assertThatThrownBy(() -> academics.getCourses(studentA, "ENROLLED"))
                .isInstanceOf(InvalidEnrollmentStatusException.class);
    }

    @Test
    void listViewsLoadEveryRelationshipInConstantQueries() {
        for (int i = 0; i < 10; i++) {
            long course = course("PERF" + i, "Performance " + i, 100);
            long lecture = id("INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES (?,?,?,?,?) RETURNING id",
                    "อาจารย์" + i, "Lecturer " + i, faculty, department, actorId);
            long semester = id("INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2030,?,?,?) RETURNING id",
                    "ภาค " + i, "Term " + i, actorId);
            enroll(studentA, section(course, "SEC-01", lecture, semester, "R" + i, "Wed"), "ACTIVE", false);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            assertThat(academics.getEnrollments(studentA, "ACTIVE")).hasSize(10);
            assertThat(academics.getLecturers(studentA, "ACTIVE")).hasSize(10);
            // Per call: Student (with placement), credential check, enrollments with every relationship.
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private void cleanDatabase() {
        db.update("DELETE FROM enrollments");
        db.update("DELETE FROM student_credentials");
        db.update("DELETE FROM students");
        db.update("UPDATE app_users SET department_id=NULL,created_by=NULL,updated_by=NULL");
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

    private long course(String code, String name, int type) {
        return id("""
                INSERT INTO courses(course_code,course_name,credit_hours,description,faculty_id,department_id,course_type,created_by)
                VALUES (?,?,3,?,?,?,?,?) RETURNING id
                """, code, name, code + " description", faculty, department, type, actorId);
    }
    private long section(long course, String number, long lecture, long semester, String room, String schedule) {
        return id("""
                INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,room,schedule,semester_id,status,created_by)
                VALUES (?,?,40,?,?,?,?,'ACTIVE',?) RETURNING id
                """, course, number, lecture, room, schedule, semester, actorId);
    }
    private long student(String code, String first, String last, String status, boolean credential) {
        long id = id("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status)
                VALUES (?,?,?,?,?) RETURNING id
                """, code, code + "@rsu.ac.th", first, last, status);
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, hash);
        return id;
    }
    private long enroll(long student, long section, String status, boolean deleted) {
        return id("""
                INSERT INTO enrollments(student_id,course_section_id,enrollment_date,status,is_deleted,created_by)
                VALUES (?,?,DATE '2026-08-15',?,?,?) RETURNING id
                """, student, section, status, deleted, actorId);
    }
    private String login(String code) throws Exception {
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("universityEmail", code + "@rsu.ac.th", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private String adminLogin() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", "academic.actor@example.test", "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private ResultActions get(String token, String path) throws Exception {
        return mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE + path), token));
    }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
    private List<Long> ids(ResultActions actions) throws Exception {
        var ids = new ArrayList<Long>();
        tree(actions.andExpect(status().isOk()).andReturn()).forEach(node -> ids.add(node.get("enrollmentId").asLong()));
        return ids;
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
