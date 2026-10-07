package com.smartAiUniversityAssistant.seniorproject.feature.course;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.course.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.course.service.CourseService;
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
class CourseIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/admin/courses";
    private static final String PASSWORD = "course actor password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired CourseService courses;
    private long actorId, facultyA, facultyB, departmentA, departmentB, programA, programB, semesterA, semesterB;
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
        db.update("DELETE FROM enrollments");
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
                VALUES ('CRS-ACTOR','Course','Actor','course.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
        facultyA = faculty("FAC-A", "Faculty A");
        facultyB = faculty("FAC-B", "Faculty B");
        departmentA = department("DEPT-A", "Department A", facultyA);
        departmentB = department("DEPT-B", "Department B", facultyB);
        programA = program("PRG-A", "Program A", departmentA);
        programB = program("PRG-B", "Program B", departmentB);
        semesterA = semester("ภาคการศึกษาที่ 1", "Semester 1");
        semesterB = semester("ภาคการศึกษาที่ 2", "Semester 2");
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void lifecycleAuditAndSoftDeleteSemantics() throws Exception {
        var credentials = db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId);
        var sessions = redis.keys("susa:test:auth:session:*");
        var body = request("  CS301  ", "  Data Structures & Algorithms  ", facultyA, departmentA);
        body.put("description", "  Study of data structures.  ");
        body.put("programId", programA);
        body.put("semesterId", semesterA);
        body.put("recommendedAcademicYear", 2);
        body.put("prerequisiteCourses", null);
        body.put("maximumStudentsPerSection", 50);
        body.remove("isActive");
        var created = perform(post(BASE), body).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("courseCode").value("CS301"))
                .andExpect(jsonPath("courseName").value("Data Structures & Algorithms"))
                .andExpect(jsonPath("creditHours").value(3))
                .andExpect(jsonPath("description").value("Study of data structures."))
                .andExpect(jsonPath("facultyId").value(facultyA))
                .andExpect(jsonPath("facultyCode").value("FAC-A"))
                .andExpect(jsonPath("facultyNameEn").value("Faculty A"))
                .andExpect(jsonPath("departmentId").value(departmentA))
                .andExpect(jsonPath("departmentCode").value("DEPT-A"))
                .andExpect(jsonPath("departmentName").value("Department A"))
                .andExpect(jsonPath("programId").value(programA))
                .andExpect(jsonPath("programCode").value("PRG-A"))
                .andExpect(jsonPath("semesterId").value(semesterA))
                .andExpect(jsonPath("semesterNameEn").value("Semester 1"))
                .andExpect(jsonPath("recommendedAcademicYear").value(2))
                .andExpect(jsonPath("prerequisiteCourses").doesNotExist())
                .andExpect(jsonPath("courseType").value(300))
                .andExpect(jsonPath("courseTypeName").value("Major Required"))
                .andExpect(jsonPath("maximumStudentsPerSection").value(50))
                .andExpect(jsonPath("isActive").value(true))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("updatedAt").doesNotExist())
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        assertThat(db.queryForMap("SELECT is_deleted,is_active,created_by,updated_by,updated_at FROM courses WHERE id=?", id))
                .containsEntry("is_deleted", false).containsEntry("is_active", true)
                .containsEntry("created_by", actorId).containsEntry("updated_by", null)
                .containsEntry("updated_at", null);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalCourses").value(1)).andExpect(jsonPath("activeCourses").value(1))
                .andExpect(jsonPath("inactiveCourses").value(0)).andExpect(jsonPath("currentSemesterCourses").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isOk())
                .andExpect(jsonPath("courseName").value("Data Structures & Algorithms"));
        var creator = db.queryForMap("SELECT created_by,created_at FROM courses WHERE id=?", id);
        clock.advance(Duration.ofSeconds(5));
        var moved = request("CS301", "Advanced Data Structures & Algorithms", facultyB, departmentB);
        moved.put("programId", programB);
        moved.put("semesterId", semesterB);
        moved.put("courseType", 200);
        moved.put("maximumStudentsPerSection", 60);
        moved.put("isActive", false);
        perform(put(BASE + "/" + id), moved).andExpect(status().isOk())
                .andExpect(jsonPath("courseName").value("Advanced Data Structures & Algorithms"))
                .andExpect(jsonPath("facultyId").value(facultyB))
                .andExpect(jsonPath("departmentId").value(departmentB))
                .andExpect(jsonPath("programId").value(programB))
                .andExpect(jsonPath("semesterId").value(semesterB))
                .andExpect(jsonPath("courseType").value(200))
                .andExpect(jsonPath("courseTypeName").value("Major Elective"))
                .andExpect(jsonPath("maximumStudentsPerSection").value(60))
                .andExpect(jsonPath("isActive").value(false))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForMap("SELECT created_by,created_at FROM courses WHERE id=?", id)).isEqualTo(creator);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("activeCourses").value(0)).andExpect(jsonPath("inactiveCourses").value(1));
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(db.queryForObject("SELECT is_deleted FROM courses WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT is_active FROM courses WHERE id=?", Boolean.class, id)).isFalse();
        assertThat(db.queryForObject("SELECT updated_by FROM courses WHERE id=?", Long.class, id)).isEqualTo(actorId);
        assertThat(db.queryForObject("SELECT count(*) FROM courses", Integer.class)).isOne();
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        perform(get(BASE + "/summary"), null).andExpect(status().isOk()).andExpect(jsonPath("totalCourses").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("COURSE_NOT_FOUND"));
        perform(delete(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(put(BASE + "/" + id), request("X1", "Gone", facultyA, departmentA)).andExpect(status().isNotFound());
        assertThat(db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId)).isEqualTo(credentials);
        assertThat(redis.keys("susa:test:auth:session:*")).isEqualTo(sessions);
    }

    @Test
    void optionalRelationsAndDefaults() throws Exception {
        var minimal = request("CS100", "Minimal Course", facultyA, departmentA);
        minimal.remove("isActive");
        perform(post(BASE), minimal).andExpect(status().isCreated())
                .andExpect(jsonPath("description").doesNotExist())
                .andExpect(jsonPath("programId").doesNotExist())
                .andExpect(jsonPath("semesterId").doesNotExist())
                .andExpect(jsonPath("recommendedAcademicYear").doesNotExist())
                .andExpect(jsonPath("prerequisiteCourses").doesNotExist())
                .andExpect(jsonPath("maximumStudentsPerSection").doesNotExist())
                .andExpect(jsonPath("isActive").value(true));
        var blanked = request("CS101", "Blanked Course", facultyA, departmentA);
        blanked.put("description", "   ");
        blanked.put("prerequisiteCourses", "  ");
        blanked.put("isActive", false);
        perform(post(BASE), blanked).andExpect(status().isCreated())
                .andExpect(jsonPath("description").doesNotExist())
                .andExpect(jsonPath("prerequisiteCourses").doesNotExist())
                .andExpect(jsonPath("isActive").value(false));
    }

    @Test
    void duplicateCourseCodeIsGlobalIncludingDeleted() throws Exception {
        long first = create("CS101", "Intro Computing", facultyA, departmentA);
        long other = create("CS102", "Other Course", facultyA, departmentA);
        var padded = request(" CS101 ", "Intro Computing", facultyB, departmentB);
        perform(post(BASE), padded).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("COURSE_CODE_ALREADY_EXISTS"));
        perform(put(BASE + "/" + other), request("CS101", "Other Course", facultyA, departmentA))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("COURSE_CODE_ALREADY_EXISTS"));
        perform(put(BASE + "/" + first), request("CS101", "Intro Computing Updated", facultyA, departmentA))
                .andExpect(status().isOk());
        perform(delete(BASE + "/" + first), null).andExpect(status().isNoContent());
        perform(post(BASE), request("CS101", "Intro Computing", facultyA, departmentA))
                .andExpect(status().isConflict());
        // Exact-case uniqueness, matching the database constraint and existing features.
        perform(post(BASE), request("cs101", "Lowercase Code", facultyA, departmentA)).andExpect(status().isCreated());
    }

    @Test
    void validatesRelationshipsOnCreateAndUpdate() throws Exception {
        long id = create("CS201", "Valid Course", facultyA, departmentA);
        var mismatch = request("CS202", "Mismatch", facultyB, departmentA);
        perform(post(BASE), mismatch).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_COURSE_RELATIONSHIP"));
        perform(put(BASE + "/" + id), request("CS201", "Mismatch", facultyB, departmentA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_COURSE_RELATIONSHIP"));
        var wrongProgram = request("CS203", "Wrong Program", facultyA, departmentA);
        wrongProgram.put("programId", programB);
        perform(post(BASE), wrongProgram).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_COURSE_RELATIONSHIP"));
        var updateWrongProgram = request("CS201", "Wrong Program", facultyA, departmentA);
        updateWrongProgram.put("programId", programB);
        perform(put(BASE + "/" + id), updateWrongProgram).andExpect(status().isBadRequest());
        for (boolean deleted : List.of(false, true)) {
            db.update("UPDATE faculties SET is_active=?,is_deleted=? WHERE id=?", deleted, deleted, facultyB);
            int expected = deleted ? 404 : 400;
            String code = deleted ? "FACULTY_NOT_FOUND" : "INVALID_FACULTY";
            perform(post(BASE), request("CS204", "Bad Faculty", facultyB, departmentB)).andExpect(status().is(expected))
                    .andExpect(jsonPath("code").value(code));
            perform(put(BASE + "/" + id), request("CS201", "Bad Faculty", facultyB, departmentB))
                    .andExpect(status().is(expected));
        }
        db.update("UPDATE faculties SET is_active=TRUE,is_deleted=FALSE WHERE id=?", facultyB);
        for (boolean deleted : List.of(false, true)) {
            db.update("UPDATE departments SET is_active=?,is_deleted=? WHERE id=?", deleted, deleted, departmentB);
            int expected = deleted ? 404 : 400;
            String code = deleted ? "DEPARTMENT_NOT_FOUND" : "INVALID_COURSE_RELATIONSHIP";
            perform(post(BASE), request("CS205", "Bad Department", facultyB, departmentB)).andExpect(status().is(expected))
                    .andExpect(jsonPath("code").value(code));
        }
        db.update("UPDATE departments SET is_active=TRUE,is_deleted=FALSE WHERE id=?", departmentB);
        for (boolean deleted : List.of(false, true)) {
            db.update("UPDATE programs SET is_active=?,is_deleted=? WHERE id=?", deleted, deleted, programB);
            int expected = deleted ? 404 : 400;
            String code = deleted ? "PROGRAM_NOT_FOUND" : "INVALID_COURSE_RELATIONSHIP";
            var body = request("CS206", "Bad Program", facultyB, departmentB);
            body.put("programId", programB);
            perform(post(BASE), body).andExpect(status().is(expected)).andExpect(jsonPath("code").value(code));
        }
        db.update("UPDATE semesters SET is_deleted=TRUE WHERE id=?", semesterB);
        var badSemester = request("CS207", "Bad Semester", facultyA, departmentA);
        badSemester.put("semesterId", semesterB);
        perform(post(BASE), badSemester).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("SEMESTER_NOT_FOUND"));
        perform(post(BASE), request("CS208", "Missing", Long.MAX_VALUE, departmentA)).andExpect(status().isNotFound());
        perform(post(BASE), request("CS209", "Missing", facultyA, Long.MAX_VALUE)).andExpect(status().isNotFound());
        var missingProgram = request("CS210", "Missing", facultyA, departmentA);
        missingProgram.put("programId", Long.MAX_VALUE);
        perform(post(BASE), missingProgram).andExpect(status().isNotFound());
        var missingSemester = request("CS211", "Missing", facultyA, departmentA);
        missingSemester.put("semesterId", Long.MAX_VALUE);
        perform(post(BASE), missingSemester).andExpect(status().isNotFound());
        assertThat(db.queryForObject("SELECT count(*) FROM courses", Integer.class)).isOne();
    }

    @Test
    void courseTypeCodesAndDisplayNames() throws Exception {
        var general = request("GEN100", "General Course", facultyA, departmentA);
        general.put("courseType", 100);
        perform(post(BASE), general).andExpect(status().isCreated())
                .andExpect(jsonPath("courseType").value(100))
                .andExpect(jsonPath("courseTypeName").value("General Education"));
        var elective = request("ELE200", "Elective Course", facultyA, departmentA);
        elective.put("courseType", 200);
        perform(post(BASE), elective).andExpect(status().isCreated())
                .andExpect(jsonPath("courseTypeName").value("Major Elective"));
        var required = request("REQ300", "Required Course", facultyA, departmentA);
        required.put("courseType", 300);
        perform(post(BASE), required).andExpect(status().isCreated())
                .andExpect(jsonPath("courseTypeName").value("Major Required"));
        for (int invalid : List.of(0, 50, 400, 999)) {
            var body = request("BAD" + invalid, "Invalid Type", facultyA, departmentA);
            body.put("courseType", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("INVALID_COURSE_TYPE"));
        }
        var missing = request("BADNULL", "Invalid Type", facultyA, departmentA);
        missing.remove("courseType");
        perform(post(BASE), missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        perform(get(BASE).param("courseType", "999"), null).andExpect(status().isBadRequest());
        perform(get(BASE).param("courseType", "200"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1));
    }

    @Test
    void prerequisiteNormalizationAndValidation() throws Exception {
        create("CS101", "Intro Computing", facultyA, departmentA);
        create("CS201", "Intermediate Computing", facultyA, departmentA);
        long gone = create("CS999", "Soon Deleted", facultyA, departmentA);
        var body = request("CS301", "Advanced Computing", facultyA, departmentA);
        body.put("prerequisiteCourses", " CS101 , CS201 , CS101 ,, ");
        perform(post(BASE), body).andExpect(status().isCreated())
                .andExpect(jsonPath("prerequisiteCourses").value("CS101,CS201"));
        var self = request("CS302", "Self Reference", facultyA, departmentA);
        self.put("prerequisiteCourses", "CS101,CS302");
        perform(post(BASE), self).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_PREREQUISITE_COURSE"));
        var missing = request("CS303", "Missing Prerequisite", facultyA, departmentA);
        missing.put("prerequisiteCourses", "CS101,ABC999");
        perform(post(BASE), missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_PREREQUISITE_COURSE"));
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        var deletedPrerequisite = request("CS304", "Deleted Prerequisite", facultyA, departmentA);
        deletedPrerequisite.put("prerequisiteCourses", "CS999");
        perform(post(BASE), deletedPrerequisite).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_PREREQUISITE_COURSE"));
        long id = create("CS305", "Update Prerequisites", facultyA, departmentA);
        var update = request("CS305", "Update Prerequisites", facultyA, departmentA);
        update.put("prerequisiteCourses", "CS201, CS101");
        perform(put(BASE + "/" + id), update).andExpect(status().isOk())
                .andExpect(jsonPath("prerequisiteCourses").value("CS201,CS101"));
        var cleared = request("CS305", "Update Prerequisites", facultyA, departmentA);
        perform(put(BASE + "/" + id), cleared).andExpect(status().isOk())
                .andExpect(jsonPath("prerequisiteCourses").doesNotExist());
    }

    @Test
    void strictInputValidationAndUnknownFields() throws Exception {
        for (String field : List.of("courseCode", "courseName", "creditHours", "facultyId", "departmentId", "courseType")) {
            var body = request("CS400", "Validation Course", facultyA, departmentA);
            body.remove(field);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : Arrays.asList("", "   ", "x".repeat(51), null)) {
            var body = request("CS400", "Validation Course", facultyA, departmentA);
            body.put("courseCode", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : Arrays.asList("", "   ", "x".repeat(256), null)) {
            var body = request("CS400", "Validation Course", facultyA, departmentA);
            body.put("courseName", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : List.of(0, -1, 1.5, "3")) {
            var body = request("CS400", "Validation Course", facultyA, departmentA);
            body.put("creditHours", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (String field : List.of("recommendedAcademicYear", "maximumStudentsPerSection")) {
            for (Object invalid : List.of(0, -1)) {
                var body = request("CS400", "Validation Course", facultyA, departmentA);
                body.put(field, invalid);
                perform(post(BASE), body).andExpect(status().isBadRequest());
            }
        }
        for (Object invalid : List.of("1", -1, 0, 1.5)) {
            for (String field : List.of("facultyId", "departmentId", "programId", "semesterId")) {
                var body = request("CS400", "Validation Course", facultyA, departmentA);
                body.put(field, invalid);
                perform(post(BASE), body).andExpect(status().isBadRequest());
            }
        }
        var longDescription = request("CS400", "Validation Course", facultyA, departmentA);
        longDescription.put("description", "d".repeat(1001));
        perform(post(BASE), longDescription).andExpect(status().isBadRequest());
        var longPrerequisites = request("CS400", "Validation Course", facultyA, departmentA);
        longPrerequisites.put("prerequisiteCourses", "p".repeat(1001));
        perform(post(BASE), longPrerequisites).andExpect(status().isBadRequest());
        for (String field : List.of("id", "createdBy", "updatedBy", "isDeleted", "sectionCount", "courseTypeName")) {
            var body = request("CS400", "Validation Course", facultyA, departmentA);
            body.put(field, 1);
            perform(post(BASE), body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        long id = create("CS401", "Update Validation", facultyA, departmentA);
        for (String field : List.of("courseCode", "courseName", "creditHours", "facultyId", "departmentId",
                "courseType", "isActive")) {
            var body = request("CS401", "Update Validation", facultyA, departmentA);
            body.put("isActive", true);
            body.remove(field);
            perform(put(BASE + "/" + id), body).andExpect(status().isBadRequest());
        }
        for (String path : List.of("0", "-1", "abc", "9223372036854775808"))
            perform(get(BASE + "/" + path), null).andExpect(status().isBadRequest());
        perform(get(BASE + "/9223372036854775807"), null).andExpect(status().isNotFound());
    }

    @Test
    void listSupportsSearchFiltersAndStablePagination() throws Exception {
        long one = create("CS301", "Data Structures", facultyA, departmentA);
        long two = create("CS401", "Database Systems", facultyB, departmentB);
        long three = create("CS302", "Data Mining", facultyA, departmentA);
        db.update("UPDATE courses SET program_id=?,semester_id=?,recommended_academic_year=2,course_type=300 WHERE id=?",
                programA, semesterA, one);
        db.update("UPDATE courses SET program_id=?,semester_id=?,recommended_academic_year=3,course_type=100 WHERE id=?",
                programB, semesterB, two);
        db.update("UPDATE courses SET is_active=FALSE WHERE id=?", three);
        long gone = create("CS900", "Gone Course", facultyA, departmentA);
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(20)).andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("sort[0]").value("createdAt,desc")).andExpect(jsonPath("sort[1]").value("id,asc"))
                .andExpect(jsonPath("content[0].sectionCount").value(0));
        for (String search : List.of("data", "DATA"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk())
                    .andExpect(jsonPath("totalElements").value(3));
        perform(get(BASE).param("search", "cs30"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        perform(get(BASE).param("facultyId", String.valueOf(facultyB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two))
                .andExpect(jsonPath("content[0].facultyCode").value("FAC-B"));
        perform(get(BASE).param("departmentId", String.valueOf(departmentA)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(2));
        perform(get(BASE).param("programId", String.valueOf(programA)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(one));
        perform(get(BASE).param("semesterId", String.valueOf(semesterB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two));
        perform(get(BASE).param("recommendedAcademicYear", "2"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(one));
        perform(get(BASE).param("courseType", "100"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two));
        perform(get(BASE).param("status", "INACTIVE"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(three));
        perform(get(BASE).param("search", "data").param("facultyId", String.valueOf(facultyA))
                .param("departmentId", String.valueOf(departmentA)).param("programId", String.valueOf(programA))
                .param("semesterId", String.valueOf(semesterA)).param("recommendedAcademicYear", "2")
                .param("courseType", "300").param("status", "ACTIVE"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(one))
                .andExpect(jsonPath("content[0].programName").value("Program A"))
                .andExpect(jsonPath("content[0].semesterNameTh").value("ภาคการศึกษาที่ 1"));
        perform(get(BASE).param("sort", "courseCode,asc").param("size", "1").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("first").value(false))
                .andExpect(jsonPath("last").value(false));
        perform(get(BASE).param("page", "99"), null).andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("last").value(true)).andExpect(jsonPath("totalElements").value(3));
        for (String query : List.of("size=0", "size=101", "page=-1", "page=2147483647&size=100",
                "facultyId=0", "departmentId=abc", "programId=-1", "semesterId=0", "recommendedAcademicYear=0",
                "courseType=50", "courseType=abc", "status=active", "status=PENDING", "includeDeleted=true",
                "sort=course_code,asc", "sort=id,up", "sort=id,asc&sort=id,desc", "page=0&page=1",
                "search=" + "x".repeat(256)))
            perform(get(BASE + "?" + query), null).andExpect(status().isBadRequest());
    }

    @Test
    void allAdministrativeRolesCanMutateAndCurrentAuthorityIsRequired() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create("CS-" + role, role + " Course", facultyA, departmentA);
            perform(put(BASE + "/" + id), request("CS-" + role, role + " Updated", facultyA, departmentA))
                    .andExpect(status().isOk());
            perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('CRS_TEST_READER','Course test reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("CRS_TEST_READER"); // Existing token still claims SUPER_ADMIN: database roles must win.
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1")))
            perform(route, null).andExpect(status().isForbidden());
        perform(post(BASE), request("CSX", "Denied", facultyA, departmentA)).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        perform(post(BASE), request("CSX", "Denied", facultyA, departmentA)).andExpect(status().isForbidden());
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
        assertThatThrownBy(() -> courses.detail(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> courses.summary(principal)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> courses.delete(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> courses.create(principal,
                new CourseCreateRequest("CS1", "Denied", 3, null, facultyA, departmentA, null, null, null, null, 300, null, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateCodeRaceIsAtomic() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> attempt = () -> {
                barrier.await();
                return perform(post(BASE), request("CS-RACE", "Race Course", facultyA, departmentA))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM courses WHERE course_code='CS-RACE'", Integer.class)).isOne();
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
    private long program(String code, String name, long department) {
        return db.queryForObject(
                "INSERT INTO programs(program_code,program_name,degree_level,department_id,created_by) VALUES (?,?,'Bachelor',?,?) RETURNING id",
                Long.class, code, name, department, actorId);
    }
    private long semester(String nameTh, String nameEn) {
        return db.queryForObject(
                "INSERT INTO semesters(academic_year,semester_name_th,semester_name_en,created_by) VALUES (2026,?,?,?) RETURNING id",
                Long.class, nameTh, nameEn, actorId);
    }
    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "course.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private long create(String code, String name, long faculty, long department) throws Exception {
        return tree(perform(post(BASE), request(code, name, faculty, department))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private Map<String, Object> request(String code, String name, long faculty, long department) {
        var body = new LinkedHashMap<String, Object>();
        body.put("courseCode", code);
        body.put("courseName", name);
        body.put("creditHours", 3);
        body.put("facultyId", faculty);
        body.put("departmentId", department);
        body.put("courseType", 300);
        body.put("isActive", true);
        return body;
    }
    private ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
