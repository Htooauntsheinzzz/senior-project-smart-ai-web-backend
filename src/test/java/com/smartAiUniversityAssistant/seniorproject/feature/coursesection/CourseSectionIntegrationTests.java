package com.smartAiUniversityAssistant.seniorproject.feature.coursesection;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.service.CourseSectionService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class CourseSectionIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/admin/course-sections";
    private static final String PASSWORD = "course section actor password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired CourseSectionService sections;
    private long actorId, facultyA, facultyB, departmentA, departmentB;
    private long lectureA, lectureA2, courseA, courseB, semester1, semester2;
    private String access;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        SecurityContextHolder.clearContext();
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
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
                VALUES ('SEC-ACTOR','Section','Actor','section.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
        facultyA = faculty("FAC-A", "Faculty A");
        facultyB = faculty("FAC-B", "Faculty B");
        departmentA = department("DEPT-A", "Department A", facultyA);
        departmentB = department("DEPT-B", "Department B", facultyB);
        lectureA = lecture("อาจารย์นิตยา จินดาขันธ์", "Nittaya Chindakharn", facultyA, departmentA);
        lectureA2 = lecture("อาจารย์สมชาย ใจดี", "Somchai Jaidee", facultyA, departmentA);
        courseA = course("CS101", "Introduction to Programming", facultyA, departmentA, 50);
        courseB = course("CS201", "Data Structures", facultyB, departmentB, 40);
        semester1 = semester("ภาคการศึกษาที่ 1", "Semester 1");
        semester2 = semester("ภาคการศึกษาที่ 2", "Semester 2");
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void lifecycleAuditCapacityDefaultAndSoftDeleteSemantics() throws Exception {
        var credentials = db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId);
        var sessions = redis.keys("susa:test:auth:session:*");
        var body = request(courseA, "  01  ", null, lectureA, semester1);
        body.remove("status");
        body.put("room", "  ");
        var created = perform(post(BASE), body).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("courseId").value(courseA))
                .andExpect(jsonPath("courseCode").value("CS101"))
                .andExpect(jsonPath("courseName").value("Introduction to Programming"))
                .andExpect(jsonPath("sectionNumber").value("01"))
                .andExpect(jsonPath("capacity").value(50))
                .andExpect(jsonPath("enrolledCount").value(0))
                .andExpect(jsonPath("isFull").value(false))
                .andExpect(jsonPath("lectureId").value(lectureA))
                .andExpect(jsonPath("lectureNameEn").value("Nittaya Chindakharn"))
                .andExpect(jsonPath("lectureNameTh").value("อาจารย์นิตยา จินดาขันธ์"))
                .andExpect(jsonPath("room").doesNotExist())
                .andExpect(jsonPath("schedule").value("Mon/Wed 08:00-09:30"))
                .andExpect(jsonPath("semesterId").value(semester1))
                .andExpect(jsonPath("semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("semesterNameTh").value("ภาคการศึกษาที่ 1"))
                .andExpect(jsonPath("status").value("ACTIVE"))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("updatedAt").doesNotExist())
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        assertThat(db.queryForMap("SELECT is_deleted,status,room,capacity,created_by,updated_by FROM course_sections WHERE id=?", id))
                .containsEntry("is_deleted", false).containsEntry("status", "ACTIVE")
                .containsEntry("room", null).containsEntry("capacity", 50)
                .containsEntry("created_by", actorId).containsEntry("updated_by", null);
        courseSectionCount(courseA, 1);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalSections").value(1)).andExpect(jsonPath("activeSections").value(1))
                .andExpect(jsonPath("closedSections").value(0)).andExpect(jsonPath("fullSections").value(0))
                .andExpect(jsonPath("totalEnrolled").value(0));
        var creator = db.queryForMap("SELECT created_by,created_at FROM course_sections WHERE id=?", id);
        clock.advance(Duration.ofSeconds(5));
        var moved = request(courseA, "01", 60, lectureA2, semester2);
        moved.put("status", "closed");
        moved.put("room", "IT-102");
        perform(put(BASE + "/" + id), moved).andExpect(status().isOk())
                .andExpect(jsonPath("capacity").value(60))
                .andExpect(jsonPath("lectureId").value(lectureA2))
                .andExpect(jsonPath("semesterId").value(semester2))
                .andExpect(jsonPath("room").value("IT-102"))
                .andExpect(jsonPath("status").value("CLOSED"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForMap("SELECT created_by,created_at FROM course_sections WHERE id=?", id)).isEqualTo(creator);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("activeSections").value(0)).andExpect(jsonPath("closedSections").value(1));
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(db.queryForObject("SELECT is_deleted FROM course_sections WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT status FROM course_sections WHERE id=?", String.class, id)).isEqualTo("CLOSED");
        assertThat(db.queryForObject("SELECT count(*) FROM course_sections", Integer.class)).isOne();
        courseSectionCount(courseA, 0);
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        perform(get(BASE + "/summary"), null).andExpect(status().isOk()).andExpect(jsonPath("totalSections").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("COURSE_SECTION_NOT_FOUND"));
        perform(delete(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(put(BASE + "/" + id), moved).andExpect(status().isNotFound());
        assertThat(db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId)).isEqualTo(credentials);
        assertThat(redis.keys("susa:test:auth:session:*")).isEqualTo(sessions);
    }

    @Test
    void duplicatesScopedToCourseSectionSemesterIncludingDeleted() throws Exception {
        long first = create(courseA, "01", lectureA, semester1);
        long otherSemester = create(courseA, "01", lectureA, semester2);
        long otherSection = create(courseA, "02", lectureA, semester1);
        perform(post(BASE), request(courseA, " 01 ", 50, lectureA, semester1))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("COURSE_SECTION_ALREADY_EXISTS"));
        perform(put(BASE + "/" + otherSemester), request(courseA, "01", 50, lectureA, semester1))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("COURSE_SECTION_ALREADY_EXISTS"));
        perform(put(BASE + "/" + otherSection), request(courseA, "02", 45, lectureA, semester1))
                .andExpect(status().isOk()).andExpect(jsonPath("capacity").value(45));
        perform(delete(BASE + "/" + first), null).andExpect(status().isNoContent());
        perform(post(BASE), request(courseA, "01", 50, lectureA, semester1))
                .andExpect(status().isConflict());
        // Exact strings: "01" and "1" are different section numbers, matching the database constraint.
        perform(post(BASE), request(courseA, "1", 50, lectureA, semester1)).andExpect(status().isCreated())
                .andExpect(jsonPath("sectionNumber").value("1"));
    }

    @Test
    void validatesCourseLecturerAndSemesterOnCreateAndUpdate() throws Exception {
        long id = create(courseA, "01", lectureA, semester1);
        long otherFacultyLecture = lecture("อาจารย์ต่างคณะ", "Other Faculty Lecture", facultyB, departmentB);
        perform(post(BASE), request(courseA, "09", 50, otherFacultyLecture, semester1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_COURSE_SECTION_LECTURER"));
        perform(put(BASE + "/" + id), request(courseA, "01", 50, otherFacultyLecture, semester1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_COURSE_SECTION_LECTURER"));
        perform(post(BASE), request(courseB, "09", 50, lectureA, semester1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_COURSE_SECTION_LECTURER"));
        db.update("UPDATE lectures SET status='INACTIVE' WHERE id=?", lectureA2);
        perform(post(BASE), request(courseA, "09", 50, lectureA2, semester1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_COURSE_SECTION_LECTURER"));
        db.update("UPDATE lectures SET is_deleted=TRUE WHERE id=?", lectureA2);
        perform(post(BASE), request(courseA, "09", 50, lectureA2, semester1)).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("LECTURE_NOT_FOUND"));
        perform(post(BASE), request(courseA, "09", 50, Long.MAX_VALUE, semester1)).andExpect(status().isNotFound());
        db.update("UPDATE courses SET is_active=FALSE WHERE id=?", courseB);
        long lectureB = lecture("อาจารย์คณะบี", "Faculty B Lecture", facultyB, departmentB);
        perform(post(BASE), request(courseB, "09", 50, lectureB, semester1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_COURSE_SECTION_COURSE"));
        db.update("UPDATE courses SET is_active=TRUE,is_deleted=TRUE WHERE id=?", courseB);
        perform(post(BASE), request(courseB, "09", 50, lectureB, semester1)).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("COURSE_NOT_FOUND"));
        perform(post(BASE), request(Long.MAX_VALUE, "09", 50, lectureA, semester1)).andExpect(status().isNotFound());
        db.update("UPDATE semesters SET is_deleted=TRUE WHERE id=?", semester2);
        perform(post(BASE), request(courseA, "09", 50, lectureA, semester2)).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("SEMESTER_NOT_FOUND"));
        perform(put(BASE + "/" + id), request(courseA, "01", 50, lectureA, semester2)).andExpect(status().isNotFound());
        perform(post(BASE), request(courseA, "09", 50, lectureA, Long.MAX_VALUE)).andExpect(status().isNotFound());
        perform(get(BASE + "/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("sectionNumber").value("01"));
        assertThat(db.queryForObject("SELECT count(*) FROM course_sections", Integer.class)).isOne();
    }

    @Test
    void strictInputValidationAndUnknownFields() throws Exception {
        for (String field : List.of("courseId", "sectionNumber", "lectureId", "semesterId")) {
            var body = request(courseA, "01", 50, lectureA, semester1);
            body.remove(field);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : Arrays.asList("", "   ", "x".repeat(21), null)) {
            var body = request(courseA, "01", 50, lectureA, semester1);
            body.put("sectionNumber", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : List.of(0, -1, "50", 1.5)) {
            var body = request(courseA, "01", 50, lectureA, semester1);
            body.put("capacity", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (String invalid : List.of("FULL", "INACTIVE", "PENDING", "DELETED")) {
            var body = request(courseA, "01", 50, lectureA, semester1);
            body.put("status", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("INVALID_COURSE_SECTION_STATUS"));
        }
        var nullStatus = request(courseA, "01", 50, lectureA, semester1);
        nullStatus.put("status", null);
        perform(post(BASE), nullStatus).andExpect(status().isBadRequest());
        for (String field : List.of("createdBy", "updatedBy", "isDeleted", "enrolledCount", "isFull",
                "academicYear", "academicYearId")) {
            var body = request(courseA, "01", 50, lectureA, semester1);
            body.put(field, 1);
            perform(post(BASE), body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        var longRoom = request(courseA, "01", 50, lectureA, semester1);
        longRoom.put("room", "r".repeat(101));
        perform(post(BASE), longRoom).andExpect(status().isBadRequest());
        long noDefaultCourse = course("CS999", "No Default Capacity", facultyA, departmentA, null);
        var noCapacity = request(noDefaultCourse, "01", null, lectureA, semester1);
        perform(post(BASE), noCapacity).andExpect(status().isBadRequest());
        perform(post(BASE), request(noDefaultCourse, "01", 30, lectureA, semester1)).andExpect(status().isCreated())
                .andExpect(jsonPath("capacity").value(30));
        long id = create(courseA, "05", lectureA, semester1);
        for (String field : List.of("courseId", "sectionNumber", "capacity", "lectureId", "semesterId", "status")) {
            var body = request(courseA, "05", 50, lectureA, semester1);
            body.remove(field);
            perform(put(BASE + "/" + id), body).andExpect(status().isBadRequest());
        }
        var closeReopen = request(courseA, "05", 50, lectureA, semester1);
        closeReopen.put("status", "CLOSED");
        perform(put(BASE + "/" + id), closeReopen).andExpect(status().isOk()).andExpect(jsonPath("status").value("CLOSED"));
        assertThat(db.queryForObject("SELECT is_deleted FROM course_sections WHERE id=?", Boolean.class, id)).isFalse();
        closeReopen.put("status", "active");
        perform(put(BASE + "/" + id), closeReopen).andExpect(status().isOk()).andExpect(jsonPath("status").value("ACTIVE"));
        for (String path : List.of("0", "-1", "abc", "9223372036854775808"))
            perform(get(BASE + "/" + path), null).andExpect(status().isBadRequest());
        perform(get(BASE + "/9223372036854775807"), null).andExpect(status().isNotFound());
    }

    @Test
    void listSupportsSearchFiltersAndStablePagination() throws Exception {
        long lectureB = lecture("อาจารย์วิภา 100%_", "Wipa Lecture", facultyB, departmentB);
        long one = create(courseA, "01", lectureA, semester1);
        long two = create(courseB, "A", lectureB, semester1);
        long three = create(courseA, "02", lectureA, semester2);
        db.update("UPDATE course_sections SET status='CLOSED' WHERE id=?", three);
        db.update("UPDATE course_sections SET room='LAB-100%_' WHERE id=?", two);
        long gone = create(courseA, "03", lectureA, semester1);
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(20)).andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("sort[0]").value("createdAt,desc")).andExpect(jsonPath("sort[1]").value("id,asc"))
                .andExpect(jsonPath("content[0].id").value(one))
                .andExpect(jsonPath("content[0].enrolledCount").value(0))
                .andExpect(jsonPath("content[0].isFull").value(false));
        for (String search : List.of("cs101", "introduction", "nittaya", "นิตยา"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk())
                    .andExpect(jsonPath("totalElements").value(2));
        for (String search : List.of("wipa", "lab-100%_"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk())
                    .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two));
        // "01" would also match course codes CS101/CS201; "02" isolates the section-number search path.
        perform(get(BASE).param("search", "02"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(three));
        perform(get(BASE).param("courseId", String.valueOf(courseA)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        perform(get(BASE).param("semesterId", String.valueOf(semester1)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        perform(get(BASE).param("lectureId", String.valueOf(lectureB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two));
        perform(get(BASE).param("status", "CLOSED"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(three));
        perform(get(BASE).param("search", "cs101").param("courseId", String.valueOf(courseA))
                .param("semesterId", String.valueOf(semester1)).param("lectureId", String.valueOf(lectureA))
                .param("status", "ACTIVE"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(one));
        perform(get(BASE).param("sort", "sectionNumber,desc").param("size", "1").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("first").value(false))
                .andExpect(jsonPath("last").value(false));
        perform(get(BASE).param("sort", "capacity,asc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("sort[0]").value("capacity,asc"));
        perform(get(BASE).param("page", "99"), null).andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("last").value(true)).andExpect(jsonPath("totalElements").value(3));
        for (String query : List.of("size=0", "size=101", "page=-1", "courseId=0", "semesterId=abc", "lectureId=-1",
                "status=FULL", "status=active", "academicYear=2026", "includeDeleted=true", "sort=course_code,asc",
                "sort=id,up", "sort=id,asc&sort=id,desc", "page=0&page=1", "search=" + "x".repeat(256)))
            perform(get(BASE + "?" + query), null).andExpect(status().isBadRequest());
    }

    @Test
    void allAdministrativeRolesCanMutateAndCurrentAuthorityIsRequired() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create(courseA, role.substring(0, 2) + "-" + role.charAt(0), lectureA, semester1);
            perform(put(BASE + "/" + id), request(courseA, "UP-" + role.substring(0, 2), 50, lectureA, semester1))
                    .andExpect(status().isOk());
            perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('SEC_TEST_READER','Section test reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("SEC_TEST_READER"); // Existing token still claims SUPER_ADMIN: database roles must win.
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1")))
            perform(route, null).andExpect(status().isForbidden());
        perform(post(BASE), request(courseA, "NO", 50, lectureA, semester1)).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        perform(post(BASE), request(courseA, "NO", 50, lectureA, semester1)).andExpect(status().isForbidden());
        db.update("UPDATE appuser_credentials SET force_password_change=FALSE WHERE user_id=?", actorId);
        mvc.perform(get(BASE).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1"), post(BASE), put(BASE + "/1")))
            mvc.perform(route).andExpect(status().isUnauthorized());
        clock.advance(Duration.ofMinutes(16));
        perform(get(BASE), null).andExpect(status().isUnauthorized());
    }

    @Test
    void directServiceCallsStillRequireAdministrativeRole() {
        var principal = new AuthenticatedUser(actorId, "unused", "unused", false, Set.of("READER"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,
                null, List.of(new SimpleGrantedAuthority("ROLE_READER"))));
        assertThatThrownBy(() -> sections.detail(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> sections.summary(principal)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> sections.delete(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> sections.create(principal, new CourseSectionCreateRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateSectionRaceIsAtomic() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> attempt = () -> {
                barrier.await();
                return perform(post(BASE), request(courseA, "RACE", 50, lectureA, semester1))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM course_sections WHERE section_number='RACE'", Integer.class)).isOne();
    }

    private void courseSectionCount(long courseId, int expected) throws Exception {
        perform(get("/api/v1/admin/courses").param("search", "CS10"), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].sectionCount").value(expected));
    }
    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }
    private long faculty(String code, String name) {
        return db.queryForObject("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES (?,?,?) RETURNING id",
                Long.class, code, name, actorId);
    }
    private long department(String code, String name, long faculty) {
        return db.queryForObject(
                "INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES (?,?,?,?) RETURNING id",
                Long.class, code, name, faculty, actorId);
    }
    private long lecture(String nameTh, String nameEn, long faculty, long department) {
        return db.queryForObject(
                "INSERT INTO lectures(lecture_name_th,lecture_name_en,faculty_id,department_id,created_by) VALUES (?,?,?,?,?) RETURNING id",
                Long.class, nameTh, nameEn, faculty, department, actorId);
    }
    private long course(String code, String name, long faculty, long department, Integer maxStudents) {
        return db.queryForObject("""
                INSERT INTO courses(course_code,course_name,credit_hours,faculty_id,department_id,course_type,maximum_students_per_section,created_by)
                VALUES (?,?,3,?,?,100,?,?) RETURNING id
                """, Long.class, code, name, faculty, department, maxStudents, actorId);
    }
    private long semester(String nameTh, String nameEn) {
        return db.queryForObject(
                "INSERT INTO semesters(semester_name_th,semester_name_en,created_by) VALUES (?,?,?) RETURNING id",
                Long.class, nameTh, nameEn, actorId);
    }
    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "section.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private long create(long course, String section, long lecture, long semester) throws Exception {
        return tree(perform(post(BASE), request(course, section, 50, lecture, semester))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private Map<String, Object> request(long course, String section, Integer capacity, long lecture, long semester) {
        var body = new LinkedHashMap<String, Object>();
        body.put("courseId", course);
        body.put("sectionNumber", section);
        body.put("capacity", capacity);
        body.put("lectureId", lecture);
        body.put("room", "IT-101");
        body.put("schedule", "Mon/Wed 08:00-09:30");
        body.put("semesterId", semester);
        body.put("status", "ACTIVE");
        return body;
    }
    private ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
