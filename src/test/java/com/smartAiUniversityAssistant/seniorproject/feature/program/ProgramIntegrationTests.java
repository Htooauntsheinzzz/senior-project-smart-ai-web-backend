package com.smartAiUniversityAssistant.seniorproject.feature.program;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.program.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.program.service.ProgramService;
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
class ProgramIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/admin/programs";
    private static final String PASSWORD = "program actor password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired ProgramService programs;
    private long actorId, facultyA, facultyB, departmentA, departmentB;
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
        db.update("DELETE FROM programs");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('PRG-ACTOR','Program','Actor','program.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
        facultyA = faculty("FAC-A", "Faculty A");
        facultyB = faculty("FAC-B", "Faculty B");
        departmentA = department("DEPT-A", "Department A", facultyA);
        departmentB = department("DEPT-B", "Department B", facultyB);
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void lifecycleAuditAndLiveDepartmentProgramCounts() throws Exception {
        var credentials = db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId);
        var sessions = redis.keys("susa:test:auth:session:*");
        var body = request("PRG-CE-BS", "Computer Engineering", " Bachelor of Engineering ", facultyA, departmentA);
        body.put("durationYears", 4);
        body.put("totalCredits", 144);
        body.remove("isActive");
        var created = perform(post(BASE), body).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("programCode").value("PRG-CE-BS"))
                .andExpect(jsonPath("programName").value("Computer Engineering"))
                .andExpect(jsonPath("degreeLevel").value("Bachelor of Engineering"))
                .andExpect(jsonPath("facultyId").value(facultyA))
                .andExpect(jsonPath("facultyCode").value("FAC-A"))
                .andExpect(jsonPath("facultyNameEn").value("Faculty A"))
                .andExpect(jsonPath("departmentId").value(departmentA))
                .andExpect(jsonPath("departmentCode").value("DEPT-A"))
                .andExpect(jsonPath("departmentName").value("Department A"))
                .andExpect(jsonPath("durationYears").value(4))
                .andExpect(jsonPath("totalCredits").value(144))
                .andExpect(jsonPath("isActive").value(true))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        var stored = db.queryForMap("SELECT created_by,created_at,duration_years,total_credits FROM programs WHERE id=?", id);
        assertThat(stored.get("created_by")).isEqualTo(actorId);
        assertThat(stored.get("duration_years")).isEqualTo(4);
        departmentCount(departmentA, 1);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalPrograms").value(1)).andExpect(jsonPath("activePrograms").value(1));
        clock.advance(Duration.ofSeconds(5));
        var updated = request("PRG-CE-MS", "Computer Engineering (Graduate)", "Master of Engineering", facultyB, departmentB);
        updated.put("isActive", false);
        perform(put(BASE + "/" + id), updated).andExpect(status().isOk())
                .andExpect(jsonPath("degreeLevel").value("Master of Engineering"))
                .andExpect(jsonPath("isActive").value(false))
                .andExpect(jsonPath("facultyId").value(facultyB))
                .andExpect(jsonPath("departmentId").value(departmentB))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForMap("SELECT created_by,created_at FROM programs WHERE id=?", id))
                .containsEntry("created_by", stored.get("created_by")).containsEntry("created_at", stored.get("created_at"));
        departmentCount(departmentA, 0);
        departmentCount(departmentB, 1);
        perform(delete("/api/v1/admin/departments/" + departmentB), null).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DEPARTMENT_HAS_PROGRAMS"));
        perform(get("/api/v1/admin/departments").param("sort", "departmentCode,asc"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("content[0].programCount").value(0))
                .andExpect(jsonPath("content[1].programCount").value(1));
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("activePrograms").value(0)).andExpect(jsonPath("inactivePrograms").value(1));
        perform(get(BASE + "/" + id), null).andExpect(status().isOk())
                .andExpect(jsonPath("departmentName").value("Department B"));
        perform(put(BASE + "/" + id), updated).andExpect(status().isOk());
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(db.queryForObject("SELECT is_deleted FROM programs WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT is_active FROM programs WHERE id=?", Boolean.class, id)).isFalse();
        departmentCount(departmentB, 0);
        perform(delete("/api/v1/admin/departments/" + departmentB), null).andExpect(status().isNoContent());
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        perform(get(BASE + "/summary"), null).andExpect(status().isOk()).andExpect(jsonPath("totalPrograms").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(delete(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(put(BASE + "/" + id), updated).andExpect(status().isNotFound());
        assertThat(db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId)).isEqualTo(credentials);
        assertThat(redis.keys("susa:test:auth:session:*")).isEqualTo(sessions);
    }

    @Test
    void degreeLevelIsFreeTextAcrossCreateUpdateAndFilter() throws Exception {
        Map<String, Object> first = request("PRG-D1", "Data Science", "Doctor of Philosophy", facultyA, departmentA);
        perform(post(BASE), first).andExpect(status().isCreated()).andExpect(jsonPath("degreeLevel").value("Doctor of Philosophy"));
        perform(post(BASE), request("PRG-D2", "Custom Study", "Custom Academic Degree Name", facultyA, departmentA))
                .andExpect(status().isCreated()).andExpect(jsonPath("degreeLevel").value("Custom Academic Degree Name"));
        perform(post(BASE), request("PRG-D3", "Thai Study", "ปริญญาตรี", facultyA, departmentA))
                .andExpect(status().isCreated()).andExpect(jsonPath("degreeLevel").value("ปริญญาตรี"));
        perform(get(BASE).param("degreeLevel", "doctor of philosophy"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].programCode").value("PRG-D1"));
        perform(get(BASE).param("degreeLevel", "Doctor of Philosophy"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        perform(get(BASE).param("degreeLevel", "Bachelor of Engineering"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        for (Object invalid : Arrays.asList("", "   ", "x".repeat(101), null)) {
            var body = request("PRG-DX", "Invalid", invalid, facultyA, departmentA);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        var numeric = request("PRG-DX", "Invalid", 123, facultyA, departmentA);
        perform(post(BASE), numeric).andExpect(status().isBadRequest());
        var array = request("PRG-DX", "Invalid", List.of("Bachelor"), facultyA, departmentA);
        perform(post(BASE), array).andExpect(status().isBadRequest());
        long id = create("PRG-D4", "Movable", "Bachelor of Science", facultyA, departmentA);
        var update = request("PRG-D4", "Movable", " Master of Science ", facultyA, departmentA);
        update.put("isActive", true);
        perform(put(BASE + "/" + id), update).andExpect(status().isOk())
                .andExpect(jsonPath("degreeLevel").value("Master of Science"));
        assertThat(db.queryForObject("SELECT degree_level FROM programs WHERE id=?", String.class, id)).isEqualTo("Master of Science");
    }

    @Test
    void duplicatesAreGlobalForCodeAndScopedForDepartmentNameDegreeIncludingDeleted() throws Exception {
        long first = create("PRG-ONE", "Shared Name", "Bachelor", facultyA, departmentA);
        long otherDept = create("PRG-TWO", "Shared Name", "Bachelor", facultyB, departmentB);
        perform(post(BASE), request("PRG-ONE", "Different", "Bachelor", facultyB, departmentB)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("PROGRAM_CODE_ALREADY_EXISTS"));
        perform(post(BASE), request("PRG-THREE", "Shared Name", "Bachelor", facultyA, departmentA)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("PROGRAM_ALREADY_EXISTS"));
        perform(post(BASE), request("PRG-FOUR", "Shared Name", "Master", facultyA, departmentA)).andExpect(status().isCreated());
        perform(put(BASE + "/" + otherDept), request("PRG-TWO", "Shared Name", "Bachelor", facultyA, departmentA))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("PROGRAM_ALREADY_EXISTS"));
        perform(put(BASE + "/" + otherDept), request("PRG-ONE", "Other", "Bachelor", facultyB, departmentB))
                .andExpect(status().isConflict());
        perform(get(BASE + "/" + otherDept), null).andExpect(status().isOk())
                .andExpect(jsonPath("departmentId").value(departmentB)).andExpect(jsonPath("updatedAt").doesNotExist());
        perform(delete(BASE + "/" + first), null).andExpect(status().isNoContent());
        perform(post(BASE), request("PRG-ONE", "New Name", "Bachelor", facultyA, departmentA)).andExpect(status().isConflict());
        perform(post(BASE), request("PRG-NEW", "Shared Name", "Bachelor", facultyA, departmentA)).andExpect(status().isConflict());
        perform(post(BASE), request("PRG-CASE", "shared name", "Bachelor", facultyA, departmentA))
                .andExpect(status().isCreated()); // Exact-case database uniqueness, matching existing features.
    }

    @Test
    void validatesFacultyDepartmentAndConsistency() throws Exception {
        long id = create("PRG-OK", "Valid", "Bachelor", facultyA, departmentA);
        for (boolean deleted : List.of(false, true)) {
            db.update("UPDATE faculties SET is_active=?,is_deleted=? WHERE id=?", deleted, deleted, facultyB);
            int expected = deleted ? 404 : 400;
            String code = deleted ? "FACULTY_NOT_FOUND" : "INVALID_FACULTY";
            perform(post(BASE), request("PRG-BAD", "Invalid", "Bachelor", facultyB, departmentB)).andExpect(status().is(expected))
                    .andExpect(jsonPath("code").value(code));
            perform(put(BASE + "/" + id), request("PRG-CHANGED", "Changed", "Bachelor", facultyB, departmentB))
                    .andExpect(status().is(expected));
        }
        db.update("UPDATE faculties SET is_active=TRUE,is_deleted=FALSE WHERE id=?", facultyB);
        db.update("UPDATE departments SET is_active=FALSE WHERE id=?", departmentB);
        perform(post(BASE), request("PRG-INA", "Inactive Dept", "Bachelor", facultyB, departmentB))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_PROGRAM_DEPARTMENT"));
        db.update("UPDATE departments SET is_active=TRUE,is_deleted=TRUE WHERE id=?", departmentB);
        perform(post(BASE), request("PRG-DEL", "Deleted Dept", "Bachelor", facultyB, departmentB))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DEPARTMENT_NOT_FOUND"));
        db.update("UPDATE departments SET is_active=TRUE,is_deleted=FALSE WHERE id=?", departmentB);
        perform(post(BASE), request("PRG-MIS", "Missing", "Bachelor", facultyB, Long.MAX_VALUE))
                .andExpect(status().isNotFound());
        // Consistency: department A belongs to faculty A, not faculty B.
        perform(post(BASE), request("PRG-MISM", "Mismatch", "Bachelor", facultyB, departmentA))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_PROGRAM_DEPARTMENT"));
        perform(get(BASE + "/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("programCode").value("PRG-OK"));
        assertThat(db.queryForObject("SELECT count(*) FROM programs", Integer.class)).isOne();
    }

    @Test
    void strictInputValidationAndUnknownFields() throws Exception {
        for (String field : List.of("programCode", "programName", "degreeLevel", "facultyId", "departmentId")) {
            var body = request("PRG-VALID", "Valid", "Bachelor", facultyA, departmentA);
            body.remove(field);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (String field : List.of("createdBy", "updatedBy", "isDeleted", "facultyName", "departmentName", "courseCount", "studentCount")) {
            var body = request("PRG-VALID", "Valid", "Bachelor", facultyA, departmentA);
            body.put(field, 1);
            perform(post(BASE), body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        for (Object invalid : List.of("1", -1, 0, 1.5)) {
            for (String field : List.of("facultyId", "departmentId")) {
                var body = request("PRG-VALID", "Valid", "Bachelor", facultyA, departmentA);
                body.put(field, invalid);
                perform(post(BASE), body).andExpect(status().isBadRequest());
            }
        }
        for (Object invalid : Arrays.asList("true", null, 1)) {
            var body = request("PRG-VALID", "Valid", "Bachelor", facultyA, departmentA);
            body.put("isActive", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : List.of(0, -1, "4 years", 1.5)) {
            for (String field : List.of("durationYears", "totalCredits")) {
                var body = request("PRG-VALID", "Valid", "Bachelor", facultyA, departmentA);
                body.put(field, invalid);
                perform(post(BASE), body).andExpect(status().isBadRequest());
            }
        }
        perform(post(BASE), request("lowercase", "Valid", "Bachelor", facultyA, departmentA)).andExpect(status().isBadRequest());
        perform(post(BASE), request("A".repeat(51), "Valid", "Bachelor", facultyA, departmentA)).andExpect(status().isBadRequest());
        perform(post(BASE), request("PRG-VALID", " ", "Bachelor", facultyA, departmentA)).andExpect(status().isBadRequest());
        var optional = request("PRG-OPT", "Optional Numbers", "Bachelor", facultyA, departmentA);
        perform(post(BASE), optional).andExpect(status().isCreated())
                .andExpect(jsonPath("durationYears").doesNotExist()).andExpect(jsonPath("totalCredits").doesNotExist());
        long id = create("PRG-UPDATE", "Update", "Bachelor", facultyA, departmentA);
        for (String field : List.of("programCode", "programName", "degreeLevel", "facultyId", "departmentId", "isActive")) {
            var body = request("PRG-UPDATE", "Update", "Bachelor", facultyA, departmentA);
            body.remove(field);
            perform(put(BASE + "/" + id), body).andExpect(status().isBadRequest());
        }
        for (String path : List.of("0", "-1", "abc", "9223372036854775808"))
            perform(get(BASE + "/" + path), null).andExpect(status().isBadRequest());
        perform(get(BASE + "/9223372036854775807"), null).andExpect(status().isNotFound());
    }

    @Test
    void listSupportsLiteralSearchFiltersAndStablePagination() throws Exception {
        long one = create("PRG-ENG", "Engineering 100%_มาลี", "Bachelor", facultyA, departmentA);
        long two = create("PRG-IT", "Information Technology", "Bachelor", facultyB, departmentB);
        long three = create("PRG-INACTIVE", "Engineering Other", "Bachelor", facultyA, departmentA);
        db.update("UPDATE programs SET is_active=FALSE WHERE id=?", three);
        long gone = create("PRG-GONE", "Engineering Gone", "Bachelor", facultyA, departmentA);
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(20)).andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("sort[1]").value("id,asc"));
        perform(get(BASE).param("search", "engineering").param("facultyId", String.valueOf(facultyA))
                        .param("departmentId", String.valueOf(departmentA)).param("status", "ACTIVE"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(one));
        for (String search : List.of("prg-eng", "100%_", "มาลี"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        perform(get(BASE).param("facultyId", String.valueOf(facultyB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(two))
                .andExpect(jsonPath("content[0].facultyCode").value("FAC-B"));
        perform(get(BASE).param("departmentId", String.valueOf(departmentB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].id").value(two));
        perform(get(BASE).param("status", "INACTIVE"), null).andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three));
        perform(get(BASE).param("sort", "programName,asc").param("size", "1").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("first").value(false)).andExpect(jsonPath("last").value(false));
        perform(get(BASE).param("sort", "degreeLevel,desc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("sort[0]").value("degreeLevel,desc"));
        perform(get(BASE).param("page", "99"), null).andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("last").value(true)).andExpect(jsonPath("totalElements").value(3));
        perform(get(BASE).param("departmentId", String.valueOf(Long.MAX_VALUE)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(0));
        for (String query : List.of("size=0", "size=101", "size=99999999999999", "page=-1", "page=2147483647&size=100",
                "facultyId=0", "departmentId=abc", "status=active", "includeDeleted=true", "sort=faculty_name,asc",
                "sort=id,up", "sort=id,asc&sort=id,desc", "page=0&page=1", "degreeLevel=" + "x".repeat(101)))
            perform(get(BASE + "?" + query), null).andExpect(status().isBadRequest());
    }

    @Test
    void allAdministrativeRolesCanMutateAndCurrentAuthorityIsRequired() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create("PRG-" + role.replace('_', '-'), role, "Bachelor", facultyA, departmentA);
            perform(put(BASE + "/" + id), request("PRG-" + role.replace('_', '-'), "Updated " + role, "Master", facultyA, departmentA))
                    .andExpect(status().isOk());
            perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('PRG_TEST_READER','Program test reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("PRG_TEST_READER"); // Existing token still claims SUPER_ADMIN: database roles must win.
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1")))
            perform(route, null).andExpect(status().isForbidden());
        perform(post(BASE), request("PRG-NO", "Denied", "Bachelor", facultyA, departmentA)).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        perform(post(BASE), request("PRG-NO", "Denied", "Bachelor", facultyA, departmentA)).andExpect(status().isForbidden());
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
        assertThatThrownBy(() -> programs.summary(principal)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> programs.delete(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> programs.create(principal, new ProgramCreateRequest())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateCodeRaceAcrossDifferentDepartmentsIsAtomic() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var first = pool.submit(() -> { barrier.await(); return perform(post(BASE), request("PRG-RACE", "Race A", "Bachelor", facultyA, departmentA)).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { barrier.await(); return perform(post(BASE), request("PRG-RACE", "Race B", "Master", facultyB, departmentB)).andReturn().getResponse().getStatus(); });
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM programs WHERE program_code='PRG-RACE'", Integer.class)).isOne();
    }

    @Test
    void programCreationRacingDepartmentDeletionCannotLeaveLiveProgramOnDeletedDepartment() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var first = pool.submit(() -> { barrier.await(); return perform(post(BASE),
                    request("PRG-DEPT-RACE", "Race", "Bachelor", facultyA, departmentA)).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { barrier.await(); return perform(delete("/api/v1/admin/departments/" + departmentA), null)
                    .andReturn().getResponse().getStatus(); });
            int creation = first.get(15, TimeUnit.SECONDS), deletion = second.get(15, TimeUnit.SECONDS);
            assertThat((creation == 201 && deletion == 409) || (creation == 404 && deletion == 204)).isTrue();
        }
        assertThat(db.queryForObject("SELECT count(*) FROM programs p JOIN departments d ON d.id=p.department_id "
                + "WHERE NOT p.is_deleted AND d.is_deleted", Integer.class)).isZero();
    }

    private void departmentCount(long id, int expected) throws Exception {
        perform(get("/api/v1/admin/departments/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("programCount").value(expected));
    }
    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }
    private long faculty(String code, String name) {
        return db.queryForObject("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES (?,?,?) RETURNING id",
                Long.class, code, name, actorId);
    }
    private long department(String code, String name, long facultyId) {
        return db.queryForObject("INSERT INTO departments(department_code,department_name,faculty_id,created_by) VALUES (?,?,?,?) RETURNING id",
                Long.class, code, name, facultyId, actorId);
    }
    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "program.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private long create(String code, String name, String degree, long facultyId, long departmentId) throws Exception {
        return tree(perform(post(BASE), request(code, name, degree, facultyId, departmentId))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private Map<String, Object> request(String code, String name, Object degree, long facultyId, long departmentId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("programCode", code);
        body.put("programName", name);
        body.put("degreeLevel", degree);
        body.put("facultyId", facultyId);
        body.put("departmentId", departmentId);
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
