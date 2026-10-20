package com.smartAiUniversityAssistant.seniorproject.feature.enrollment;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.service.EnrollmentService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class EnrollmentIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/admin/enrollments";
    private static final String SECTIONS = "/api/v1/admin/course-sections";
    private static final String PASSWORD = "enrollment actor password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired EnrollmentService enrollments;
    private long actorId, faculty, department, lecture, courseA, courseB, semester1, semester2;
    private long studentA, studentB, studentC;
    private long sectionA1, sectionA2, sectionA1Next, sectionB1;
    private String access;
    private Instant start;
    private LocalDate today;

    @BeforeEach
    void fixture() throws Exception {
        start = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        clock.set(start);
        today = LocalDate.ofInstant(start, ZoneOffset.UTC);
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
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
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('ENR-ACTOR','Enrollment','Actor','enrollment.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
        faculty = db.queryForObject("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES ('FAC','Faculty',?) RETURNING id",
                Long.class, actorId);
        department = db.queryForObject(
                "INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES ('DEPT','Department',?,?) RETURNING id",
                Long.class, faculty, actorId);
        lecture = db.queryForObject(
                "INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES ('อาจารย์นิตยา','Nittaya Chindakharn',?,?,?) RETURNING id",
                Long.class, faculty, department, actorId);
        courseA = course("CS301", "Data Structures & Algorithms");
        courseB = course("CS302", "Operating Systems");
        semester1 = semester("ภาคการศึกษาที่ 1", "Semester 1");
        semester2 = semester("ภาคการศึกษาที่ 2", "Semester 2");
        sectionA1 = section(courseA, "SEC-01", 2, semester1, "ACTIVE");
        sectionA2 = section(courseA, "SEC-02", 40, semester1, "ACTIVE");
        sectionA1Next = section(courseA, "SEC-01", 40, semester2, "ACTIVE");
        sectionB1 = section(courseB, "SEC-01", 40, semester1, "ACTIVE");
        studentA = student("STD-2024-0001", "Thanapon", "Srisuk", "ACTIVE");
        studentB = student("STD-2024-0002", "Malee", "Chaiyo", "ACTIVE");
        studentC = student("STD-2024-0003", "Somsak", "Dee", "ACTIVE");
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void createReturnsDerivedFieldsAndPersistsOnlyEnrollmentColumns() throws Exception {
        var created = perform(post(BASE), body(studentA, sectionA1, "active")).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("studentId").value(studentA))
                .andExpect(jsonPath("studentCode").value("STD-2024-0001"))
                .andExpect(jsonPath("studentFirstName").value("Thanapon"))
                .andExpect(jsonPath("studentLastName").value("Srisuk"))
                .andExpect(jsonPath("universityEmail").value("std-2024-0001@example.test"))
                .andExpect(jsonPath("courseId").value(courseA))
                .andExpect(jsonPath("courseCode").value("CS301"))
                .andExpect(jsonPath("courseName").value("Data Structures & Algorithms"))
                .andExpect(jsonPath("courseSectionId").value(sectionA1))
                .andExpect(jsonPath("sectionNumber").value("SEC-01"))
                .andExpect(jsonPath("semesterId").value(semester1))
                .andExpect(jsonPath("semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("semesterNameTh").value("ภาคการศึกษาที่ 1"))
                .andExpect(jsonPath("lectureId").value(lecture))
                .andExpect(jsonPath("lectureNameEn").value("Nittaya Chindakharn"))
                .andExpect(jsonPath("room").value("IT-101"))
                .andExpect(jsonPath("capacity").value(2))
                .andExpect(jsonPath("enrolledCount").value(1))
                .andExpect(jsonPath("isFull").value(false))
                .andExpect(jsonPath("enrollmentDate").value(today.toString()))
                .andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(start.toString()))
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        assertThat(db.queryForMap("SELECT student_id,course_section_id,enrollment_date,status,is_deleted,created_by FROM enrollments WHERE id=?", id))
                .containsEntry("student_id", studentA).containsEntry("course_section_id", sectionA1)
                .containsEntry("enrollment_date", java.sql.Date.valueOf(today))
                .containsEntry("status", "ACTIVE").containsEntry("is_deleted", false).containsEntry("created_by", actorId);
        var dated = body(studentB, sectionA1, "PENDING");
        dated.put("enrollmentDate", "2026-07-01");
        perform(post(BASE), dated).andExpect(status().isCreated())
                .andExpect(jsonPath("enrollmentDate").value("2026-07-01"))
                .andExpect(jsonPath("enrolledCount").value(2)).andExpect(jsonPath("isFull").value(true));
        perform(get(BASE + "/" + id), null).andExpect(status().isOk())
                .andExpect(jsonPath("enrolledCount").value(2)).andExpect(jsonPath("isFull").value(true));
        // Derived and audit fields are not accepted as input.
        for (String field : List.of("courseId", "semesterId", "academicYear", "createdBy", "studentCode", "capacity")) {
            var extra = body(studentC, sectionB1, "ACTIVE");
            extra.put(field, 1);
            perform(post(BASE), extra).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        for (var invalid : List.of(Map.of("courseSectionId", sectionB1, "status", "ACTIVE"),
                Map.of("studentId", studentC, "status", "ACTIVE"), Map.of("studentId", studentC, "courseSectionId", sectionB1),
                Map.of("studentId", 0, "courseSectionId", sectionB1, "status", "ACTIVE")))
            perform(post(BASE), invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        for (String status : List.of("APPROVED", "CANCELLED", "COMPLETED", "FULL", "INACTIVE"))
            perform(post(BASE), body(studentC, sectionB1, status)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("INVALID_ENROLLMENT_STATUS"));
    }

    @Test
    void studentAndSectionEligibilityAreEnforced() throws Exception {
        for (String status : List.of("PENDING", "INACTIVE", "SUSPENDED")) {
            long student = student("STD-" + status, status, "Student", status);
            perform(post(BASE), body(student, sectionA2, "ACTIVE")).andExpect(status().isConflict())
                    .andExpect(jsonPath("code").value("ENROLLMENT_STUDENT_NOT_ELIGIBLE"));
        }
        db.update("UPDATE students SET is_deleted=TRUE WHERE id=?", studentC);
        perform(post(BASE), body(studentC, sectionA2, "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ENROLLMENT_STUDENT_NOT_ELIGIBLE"));
        perform(post(BASE), body(999_999, sectionA2, "ACTIVE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("STUDENT_NOT_FOUND"));
        db.update("UPDATE course_sections SET status='CLOSED' WHERE id=?", sectionB1);
        perform(post(BASE), body(studentA, sectionB1, "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_SECTION_NOT_AVAILABLE"));
        db.update("UPDATE course_sections SET is_deleted=TRUE WHERE id=?", sectionA1Next);
        perform(post(BASE), body(studentA, sectionA1Next, "ACTIVE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("COURSE_SECTION_NOT_FOUND"));
        perform(post(BASE), body(studentA, 999_999, "ACTIVE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("COURSE_SECTION_NOT_FOUND"));
        assertThat(db.queryForObject("SELECT count(*) FROM enrollments", Integer.class)).isZero();
    }

    @Test
    void duplicateCourseSemesterAndCapacityRules() throws Exception {
        long first = create(studentA, sectionA1, "ACTIVE");
        perform(post(BASE), body(studentA, sectionA1, "PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ENROLLMENT_ALREADY_EXISTS"));
        perform(post(BASE), body(studentA, sectionA2, "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("STUDENT_ALREADY_ENROLLED_IN_COURSE"));
        // Same course in another semester, and another course in the same semester, are fine.
        create(studentA, sectionA1Next, "ACTIVE");
        create(studentA, sectionB1, "PENDING");
        create(studentB, sectionA1, "PENDING");
        perform(post(BASE), body(studentC, sectionA1, "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_SECTION_FULL"))
                .andExpect(jsonPath("path").value(BASE));
        // WITHDRAWN/DROPPED release the seat and the same-course hold.
        perform(patch(BASE + "/" + first + "/status"), Map.of("status", "WITHDRAWN")).andExpect(status().isOk())
                .andExpect(jsonPath("enrolledCount").value(1)).andExpect(jsonPath("isFull").value(false));
        create(studentC, sectionA1, "ACTIVE");
        create(studentA, sectionA2, "ACTIVE");
        // A soft-deleted row still reserves its Student + Section pair (database UNIQUE constraint).
        long deleted = create(studentB, sectionB1, "ACTIVE");
        perform(delete(BASE + "/" + deleted), null).andExpect(status().isNoContent());
        perform(post(BASE), body(studentB, sectionB1, "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ENROLLMENT_ALREADY_EXISTS"));
    }

    @Test
    void statusTransitionsFollowTheMatrix() throws Exception {
        long pending = create(studentA, sectionA2, "PENDING");
        clock.advance(Duration.ofMinutes(5));
        perform(patch(BASE + "/" + pending + "/status"), Map.of("status", " active ")).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(start.plusSeconds(300).toString()));
        clock.advance(Duration.ofMinutes(5));
        perform(patch(BASE + "/" + pending + "/status"), Map.of("status", "ACTIVE")).andExpect(status().isOk())
                .andExpect(jsonPath("updatedAt").value(start.plusSeconds(300).toString()));
        perform(patch(BASE + "/" + pending + "/status"), Map.of("status", "PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("INVALID_ENROLLMENT_STATUS_TRANSITION"))
                .andExpect(jsonPath("path").value(BASE + "/" + pending + "/status"));
        perform(patch(BASE + "/" + pending + "/status"), Map.of("status", "DROPPED")).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("DROPPED"));
        for (String target : List.of("ACTIVE", "PENDING", "WITHDRAWN"))
            perform(patch(BASE + "/" + pending + "/status"), Map.of("status", target)).andExpect(status().isConflict())
                    .andExpect(jsonPath("code").value("INVALID_ENROLLMENT_STATUS_TRANSITION"));
        perform(patch(BASE + "/" + pending + "/status"), Map.of("status", "DROPPED")).andExpect(status().isOk());
        for (String start : List.of("PENDING", "ACTIVE")) {
            for (String end : List.of("WITHDRAWN", "DROPPED")) {
                long student = student("STD-" + start + end, "T", "S", "ACTIVE");
                long id = create(student, sectionB1, start);
                perform(patch(BASE + "/" + id + "/status"), Map.of("status", end)).andExpect(status().isOk())
                        .andExpect(jsonPath("status").value(end));
                assertThat(db.queryForObject("SELECT is_deleted FROM enrollments WHERE id=?", Boolean.class, id)).isFalse();
                perform(patch(BASE + "/" + id + "/status"), Map.of("status", "ACTIVE")).andExpect(status().isConflict());
            }
        }
        perform(patch(BASE + "/" + pending + "/status"), Map.of("status", "CANCELLED")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ENROLLMENT_STATUS"));
        perform(patch(BASE + "/" + pending + "/status"), Map.of()).andExpect(status().isBadRequest());
        perform(patch(BASE + "/999999/status"), Map.of("status", "ACTIVE")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    void updateMovesOnlyPendingEnrollmentsAndRevalidates() throws Exception {
        long pending = create(studentA, sectionA2, "PENDING");
        create(studentB, sectionA1, "ACTIVE");
        create(studentC, sectionA1, "ACTIVE");
        clock.advance(Duration.ofMinutes(1));
        perform(put(BASE + "/" + pending), body(studentA, sectionA1, "PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_SECTION_FULL"));
        perform(put(BASE + "/" + pending), body(studentB, sectionA2, "PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("STUDENT_ALREADY_ENROLLED_IN_COURSE"));
        var moved = body(studentA, sectionB1, "PENDING");
        moved.put("enrollmentDate", "2026-08-20");
        perform(put(BASE + "/" + pending), moved).andExpect(status().isOk())
                .andExpect(jsonPath("courseSectionId").value(sectionB1)).andExpect(jsonPath("courseCode").value("CS302"))
                .andExpect(jsonPath("enrollmentDate").value("2026-08-20"))
                .andExpect(jsonPath("updatedBy").value(actorId)).andExpect(jsonPath("updatedAt").value(start.plusSeconds(60).toString()));
        long other = create(studentC, sectionB1, "PENDING");
        perform(put(BASE + "/" + pending), body(studentC, sectionB1, "PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ENROLLMENT_ALREADY_EXISTS"));
        perform(put(BASE + "/" + other), body(studentB, sectionB1, "ACTIVE")).andExpect(status().isOk())
                .andExpect(jsonPath("studentId").value(studentB)).andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("enrollmentDate").value(today.toString()));
        perform(put(BASE + "/" + other), body(studentC, sectionB1, "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ENROLLMENT_UPDATE_NOT_ALLOWED"));
        perform(put(BASE + "/" + other), body(studentB, sectionA1Next, "ACTIVE")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ENROLLMENT_UPDATE_NOT_ALLOWED"));
        var redated = body(studentB, sectionB1, "ACTIVE");
        redated.put("enrollmentDate", "2026-08-01");
        perform(put(BASE + "/" + other), redated).andExpect(status().isOk())
                .andExpect(jsonPath("enrollmentDate").value("2026-08-01"));
        perform(put(BASE + "/" + other), body(studentB, sectionB1, "PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("INVALID_ENROLLMENT_STATUS_TRANSITION"));
        long inactive = student("STD-INACTIVE", "In", "Active", "INACTIVE");
        perform(put(BASE + "/" + pending), body(inactive, sectionB1, "PENDING")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("ENROLLMENT_STUDENT_NOT_ELIGIBLE"));
    }

    @Test
    void listSearchFiltersSortingAndSummary() throws Exception {
        long one = create(studentA, sectionA1, "ACTIVE", "2026-08-15");
        long two = create(studentB, sectionB1, "PENDING", "2026-08-16");
        long three = create(studentC, sectionA1Next, "ACTIVE", "2026-08-17");
        long four = create(studentA, sectionB1, "ACTIVE", "2026-08-17");
        perform(patch(BASE + "/" + four + "/status"), Map.of("status", "WITHDRAWN")).andExpect(status().isOk());
        long five = create(studentB, sectionA2, "ACTIVE", "2026-08-17");
        perform(patch(BASE + "/" + five + "/status"), Map.of("status", "DROPPED")).andExpect(status().isOk());
        long gone = create(studentC, sectionB1, "ACTIVE");
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("ENROLLMENT_NOT_FOUND"));
        perform(get(BASE + "/" + gone), null).andExpect(status().isNotFound());
        assertThat(db.queryForObject("SELECT is_deleted FROM enrollments WHERE id=?", Boolean.class, gone)).isTrue();

        perform(get(BASE), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(5)).andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(20)).andExpect(jsonPath("first").value(true))
                .andExpect(jsonPath("last").value(true))
                .andExpect(jsonPath("sort[0]").value("enrollmentDate,desc"))
                .andExpect(jsonPath("content[4].id").value(one))
                .andExpect(jsonPath("content[4].studentName").value("Thanapon Srisuk"))
                .andExpect(jsonPath("content[4].studentCode").value("STD-2024-0001"))
                .andExpect(jsonPath("content[4].courseCode").value("CS301"))
                .andExpect(jsonPath("content[4].sectionNumber").value("SEC-01"))
                .andExpect(jsonPath("content[4].semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("content[4].enrollmentDate").value("2026-08-15"));
        Map<String, List<Long>> cases = new LinkedHashMap<>();
        cases.put("search=std-2024-0002", List.of(two, five));
        cases.put("search=MALEE", List.of(two, five));
        cases.put("search=srisuk", List.of(one, four));
        cases.put("search=std-2024-0003@example", List.of(three));
        cases.put("search=cs302", List.of(two, four));
        cases.put("search=operating", List.of(two, four));
        cases.put("search=%25", List.of());
        cases.put("studentId=" + studentA, List.of(one, four));
        cases.put("courseId=" + courseA, List.of(one, three, five));
        cases.put("courseSectionId=" + sectionB1, List.of(two, four));
        cases.put("semesterId=" + semester2, List.of(three));
        cases.put("status=PENDING", List.of(two));
        cases.put("status=WITHDRAWN", List.of(four));
        cases.put("enrollmentDate=2026-08-16", List.of(two));
        cases.put("courseId=" + courseA + "&semesterId=" + semester1 + "&status=ACTIVE", List.of(one));
        for (var entry : cases.entrySet()) {
            var ids = new ArrayList<Long>();
            tree(perform(get(BASE + "?" + entry.getKey() + "&sort=id,asc"), null).andExpect(status().isOk()).andReturn())
                    .get("content").forEach(node -> ids.add(node.get("id").asLong()));
            assertThat(ids).as(entry.getKey()).containsExactlyElementsOf(entry.getValue());
        }
        perform(get(BASE).param("sort", "enrollmentDate,asc").param("size", "2").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("first").value(false))
                .andExpect(jsonPath("last").value(false));
        perform(get(BASE).param("sort", "status,asc").param("sort", "createdAt,desc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].status").value("ACTIVE"));
        for (String query : List.of("size=0", "size=101", "page=-1", "studentId=0", "courseId=abc", "status=active",
                "status=FULL", "academicYear=2026", "enrollmentDate=15-08-2026", "sort=studentCode,asc", "sort=id,up",
                "includeDeleted=true", "page=0&page=1", "search=" + "x".repeat(256)))
            perform(get(BASE + "?" + query), null).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("VALIDATION_ERROR"));

        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalEnrollments").value(5)).andExpect(jsonPath("activeEnrollments").value(2))
                .andExpect(jsonPath("pendingEnrollments").value(1)).andExpect(jsonPath("withdrawnEnrollments").value(1))
                .andExpect(jsonPath("droppedEnrollments").value(1)).andExpect(jsonPath("withdrawnOrDropped").value(2));
        perform(get(BASE + "/abc"), null).andExpect(status().isNotFound());
    }

    @Test
    void courseSectionsReportRealOccupancyAndGuardCapacity() throws Exception {
        create(studentA, sectionA1, "ACTIVE");
        create(studentB, sectionA1, "PENDING");
        long withdrawn = create(studentC, sectionB1, "ACTIVE");
        perform(patch(BASE + "/" + withdrawn + "/status"), Map.of("status", "WITHDRAWN")).andExpect(status().isOk());
        perform(get(SECTIONS + "/" + sectionA1), null).andExpect(status().isOk())
                .andExpect(jsonPath("enrolledCount").value(2)).andExpect(jsonPath("isFull").value(true));
        perform(get(SECTIONS).param("courseId", String.valueOf(courseB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].enrolledCount").value(0)).andExpect(jsonPath("content[0].isFull").value(false));
        perform(get(SECTIONS + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("fullSections").value(1)).andExpect(jsonPath("totalEnrolled").value(2));
        var lowered = new LinkedHashMap<String, Object>(Map.of("courseId", courseA, "sectionNumber", "SEC-01",
                "capacity", 1, "lectureId", lecture, "semesterId", semester1, "status", "ACTIVE"));
        perform(put(SECTIONS + "/" + sectionA1), lowered).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_SECTION_CAPACITY_BELOW_ENROLLED"));
        lowered.put("capacity", 3);
        perform(put(SECTIONS + "/" + sectionA1), lowered).andExpect(status().isOk())
                .andExpect(jsonPath("enrolledCount").value(2)).andExpect(jsonPath("isFull").value(false));
    }

    @Test
    void databaseConstraintsRejectUnsupportedStatuses() {
        String insert = "INSERT INTO enrollments(student_id,course_section_id,status,created_by) VALUES (?,?,?,?)";
        var students = List.of(studentA, studentB, studentC, student("STD-DB", "D", "B", "ACTIVE"));
        var statuses = List.of("PENDING", "ACTIVE", "WITHDRAWN", "DROPPED");
        for (int i = 0; i < statuses.size(); i++)
            db.update(insert, students.get(i), sectionA2, statuses.get(i), actorId);
        assertThat(db.queryForObject("SELECT enrollment_date FROM enrollments WHERE student_id=?", LocalDate.class, studentA))
                .isNotNull();
        for (String status : Arrays.asList("CANCELLED", "COMPLETED", "APPROVED", "WAITING", "REJECTED", "INACTIVE",
                "SUSPENDED", "FULL", "active", "Active", " ACTIVE ", "", null))
            assertThatThrownBy(() -> db.update(insert, studentA, sectionB1, status, actorId))
                    .as(String.valueOf(status)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE enrollments SET status='CANCELLED' WHERE student_id=?", studentA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.update("UPDATE enrollments SET status='WITHDRAWN' WHERE student_id=?", studentA)).isOne();
    }

    @Test
    void allAdministrativeRolesAndCurrentAuthorityAreRequired() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create(studentA, sectionA2, "PENDING");
            perform(patch(BASE + "/" + id + "/status"), Map.of("status", "ACTIVE")).andExpect(status().isOk());
            perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
            db.update("DELETE FROM enrollments WHERE id=?", id);
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('ENR_TEST_READER','Enrollment test reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("ENR_TEST_READER");
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1")))
            perform(route, null).andExpect(status().isForbidden());
        perform(post(BASE), body(studentA, sectionA2, "ACTIVE")).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        db.update("UPDATE appuser_credentials SET force_password_change=FALSE WHERE user_id=?", actorId);
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1"), post(BASE),
                put(BASE + "/1"), patch(BASE + "/1/status")))
            mvc.perform(route).andExpect(status().isUnauthorized());
        var principal = new AuthenticatedUser(actorId, "unused", "unused", false, Set.of("READER"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_READER"))));
        assertThatThrownBy(() -> enrollments.summary(principal)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> enrollments.delete(principal, 1)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentRequestsCannotOverbookOrDuplicate() throws Exception {
        create(studentA, sectionA1, "ACTIVE"); // one seat left
        assertThat(race(() -> body(studentB, sectionA1, "ACTIVE"), () -> body(studentC, sectionA1, "ACTIVE")))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(db.queryForObject("SELECT count(*) FROM enrollments WHERE course_section_id=?", Integer.class, sectionA1))
                .isEqualTo(2);
        assertThat(race(() -> body(studentB, sectionB1, "ACTIVE"), () -> body(studentB, sectionB1, "ACTIVE")))
                .containsExactlyInAnyOrder(201, 409);
        // Two different sections of the same course and semester for one student.
        long student = student("STD-RACE", "Race", "Student", "ACTIVE");
        long extra = section(courseA, "SEC-03", 40, semester1, "ACTIVE");
        assertThat(race(() -> body(student, sectionA2, "ACTIVE"), () -> body(student, extra, "ACTIVE")))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(db.queryForObject("SELECT count(*) FROM enrollments WHERE student_id=?", Integer.class, student)).isOne();
    }

    private List<Integer> race(Callable<Map<String, Object>> first, Callable<Map<String, Object>> second) throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var futures = new ArrayList<Future<Integer>>();
            for (var body : List.of(first, second))
                futures.add(pool.submit(() -> {
                    var payload = body.call();
                    barrier.await();
                    return perform(post(BASE), payload).andReturn().getResponse().getStatus();
                }));
            var result = new ArrayList<Integer>();
            for (var future : futures) result.add(future.get(15, TimeUnit.SECONDS));
            return result;
        }
    }

    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }
    private long course(String code, String name) {
        return db.queryForObject("""
                INSERT INTO courses(course_code,course_name,credit_hours,faculty_id,department_id,course_type,created_by)
                VALUES (?,?,3,?,?,100,?) RETURNING id
                """, Long.class, code, name, faculty, department, actorId);
    }
    private long semester(String nameTh, String nameEn) {
        return db.queryForObject(
                "INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,?,?,?) RETURNING id",
                Long.class, nameTh, nameEn, actorId);
    }
    private long section(long course, String number, int capacity, long semester, String status) {
        return db.queryForObject("""
                INSERT INTO course_sections(course_id,section_number,capacity,lecture_id,room,schedule,semester_id,status,created_by)
                VALUES (?,?,?,?,'IT-101','Mon 09:00-12:00',?,?,?) RETURNING id
                """, Long.class, course, number, capacity, lecture, semester, status, actorId);
    }
    private long student(String code, String first, String last, String status) {
        return db.queryForObject("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, code, code.toLowerCase(Locale.ROOT) + "@example.test", first, last, status);
    }
    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "enrollment.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private long create(long student, long section, String status) throws Exception {
        return tree(perform(post(BASE), body(student, section, status)).andExpect(status().isCreated()).andReturn())
                .get("id").asLong();
    }
    private long create(long student, long section, String status, String date) throws Exception {
        var body = body(student, section, status);
        body.put("enrollmentDate", date);
        return tree(perform(post(BASE), body).andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private Map<String, Object> body(long student, long section, String status) {
        var body = new LinkedHashMap<String, Object>();
        body.put("studentId", student);
        body.put("courseSectionId", section);
        body.put("status", status);
        return body;
    }
    private ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
