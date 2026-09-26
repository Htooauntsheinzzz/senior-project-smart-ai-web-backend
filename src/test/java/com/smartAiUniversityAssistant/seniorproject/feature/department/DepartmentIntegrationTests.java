package com.smartAiUniversityAssistant.seniorproject.feature.department;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.department.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.department.service.DepartmentService;
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
class DepartmentIntegrationTests extends IntegrationSupport {
    private static final String BASE = "/api/v1/admin/departments";
    private static final String PASSWORD = "department actor password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired DepartmentService departments;
    private long actorId, facultyA, facultyB;
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
                VALUES ('DEPT-ACTOR','Department','Actor','department.actor@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(PASSWORD));
        setRole("SUPER_ADMIN");
        access = login();
        facultyA = faculty("FAC-A", "Faculty A");
        facultyB = faculty("FAC-B", "Faculty B");
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void lifecycleAuditAndLiveFacultyCounts() throws Exception {
        var credentials = db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId);
        var sessions = redis.keys("susa:test:auth:session:*");
        var body = request("  DEPT-CE  ", "  Computer Engineering  ", facultyA);
        body.remove("isActive");
        var created = perform(post(BASE), body).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("departmentCode").value("DEPT-CE"))
                .andExpect(jsonPath("departmentName").value("Computer Engineering"))
                .andExpect(jsonPath("isActive").value(true))
                .andExpect(jsonPath("facultyId").value(facultyA))
                .andExpect(jsonPath("facultyCode").value("FAC-A"))
                .andExpect(jsonPath("facultyNameEn").value("Faculty A"))
                .andExpect(jsonPath("programCount").value(0))
                .andExpect(jsonPath("courseCount").value(0))
                .andExpect(jsonPath("studentCount").value(0))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("isDeleted").doesNotExist()).andReturn();
        long id = tree(created).get("id").asLong();
        var creator = db.queryForMap("SELECT created_by,created_at FROM departments WHERE id=?", id);
        facultyCount(facultyA, 1);
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalDepartments").value(1)).andExpect(jsonPath("activeDepartments").value(1));
        perform(get("/api/v1/admin/faculties/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalDepartments").value(1));
        clock.advance(Duration.ofSeconds(5));
        var updated = request("DEPT-MOVED", "Moved Engineering", facultyB);
        updated.put("isActive", false);
        perform(put(BASE + "/" + id), updated).andExpect(status().isOk())
                .andExpect(jsonPath("isActive").value(false)).andExpect(jsonPath("facultyId").value(facultyB))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForMap("SELECT created_by,created_at FROM departments WHERE id=?", id)).isEqualTo(creator);
        facultyCount(facultyA, 0);
        facultyCount(facultyB, 1);
        perform(get("/api/v1/admin/faculties").param("sort", "facultyCode,asc"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].departmentCount").value(0))
                .andExpect(jsonPath("content[1].departmentCount").value(1));
        perform(get(BASE + "/summary"), null).andExpect(status().isOk())
                .andExpect(jsonPath("activeDepartments").value(0)).andExpect(jsonPath("inactiveDepartments").value(1));
        perform(get(BASE + "/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("facultyNameEn").value("Faculty B"));
        perform(put(BASE + "/" + id), updated).andExpect(status().isOk());
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(db.queryForObject("SELECT is_deleted FROM departments WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT is_active FROM departments WHERE id=?", Boolean.class, id)).isFalse();
        facultyCount(facultyB, 0);
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        perform(get(BASE + "/summary"), null).andExpect(status().isOk()).andExpect(jsonPath("totalDepartments").value(0));
        perform(get("/api/v1/admin/faculties/summary"), null).andExpect(status().isOk()).andExpect(jsonPath("totalDepartments").value(0));
        perform(get(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(delete(BASE + "/" + id), null).andExpect(status().isNotFound());
        perform(put(BASE + "/" + id), updated).andExpect(status().isNotFound());
        assertThat(db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", actorId)).isEqualTo(credentials);
        assertThat(redis.keys("susa:test:auth:session:*")).isEqualTo(sessions);
    }

    @Test
    void duplicatesAreGlobalForCodeAndScopedForNameIncludingDeleted() throws Exception {
        long first = create("DEPT-ONE", "Shared Name", facultyA);
        long other = create("DEPT-TWO", "Shared Name", facultyB);
        perform(post(BASE), request("DEPT-ONE", "Different", facultyB)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DEPARTMENT_CODE_ALREADY_EXISTS"));
        perform(post(BASE), request("DEPT-THREE", "Shared Name", facultyA)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DEPARTMENT_NAME_ALREADY_EXISTS"));
        perform(put(BASE + "/" + other), request("DEPT-TWO", "Shared Name", facultyA)).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("DEPARTMENT_NAME_ALREADY_EXISTS"));
        perform(put(BASE + "/" + other), request("DEPT-ONE", "Other", facultyB)).andExpect(status().isConflict());
        perform(get(BASE + "/" + other), null).andExpect(status().isOk())
                .andExpect(jsonPath("facultyId").value(facultyB)).andExpect(jsonPath("updatedAt").doesNotExist());
        perform(delete(BASE + "/" + first), null).andExpect(status().isNoContent());
        perform(post(BASE), request("DEPT-ONE", "New Name", facultyA)).andExpect(status().isConflict());
        perform(post(BASE), request("DEPT-NEW", "Shared Name", facultyA)).andExpect(status().isConflict());
        create("DEPT-CASE", "shared name", facultyA); // Exact-case database uniqueness, matching existing features.
    }

    @Test
    void validatesFacultyOnCreateAndUpdate() throws Exception {
        long id = create("DEPT-OK", "Valid", facultyA);
        for (boolean deleted : List.of(false, true)) {
            db.update("UPDATE faculties SET is_active=?,is_deleted=? WHERE id=?", deleted, deleted, facultyB);
            int expected = deleted ? 404 : 400;
            String code = deleted ? "FACULTY_NOT_FOUND" : "INVALID_FACULTY";
            perform(post(BASE), request("DEPT-BAD", "Invalid", facultyB)).andExpect(status().is(expected))
                    .andExpect(jsonPath("code").value(code));
            perform(put(BASE + "/" + id), request("DEPT-CHANGED", "Changed", facultyB)).andExpect(status().is(expected));
        }
        perform(post(BASE), request("DEPT-MISSING", "Missing", Long.MAX_VALUE)).andExpect(status().isNotFound());
        perform(get(BASE + "/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("departmentCode").value("DEPT-OK"));
        assertThat(db.queryForObject("SELECT count(*) FROM departments", Integer.class)).isOne();
    }

    @Test
    void strictInputValidationAndUnknownFields() throws Exception {
        for (String field : List.of("departmentCode", "departmentName", "facultyId")) {
            var body = request("DEPT-VALID", "Valid", facultyA);
            body.remove(field);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (String field : List.of("createdBy", "updatedBy", "isDeleted", "facultyName", "programCount")) {
            var body = request("DEPT-VALID", "Valid", facultyA);
            body.put(field, 1);
            perform(post(BASE), body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        for (Object invalid : List.of("1", -1, 0, 1.5)) {
            var body = request("DEPT-VALID", "Valid", facultyA);
            body.put("facultyId", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        for (Object invalid : Arrays.asList("true", null, 1)) {
            var body = request("DEPT-VALID", "Valid", facultyA);
            body.put("isActive", invalid);
            perform(post(BASE), body).andExpect(status().isBadRequest());
        }
        perform(post(BASE), request("lowercase", "Valid", facultyA)).andExpect(status().isBadRequest());
        perform(post(BASE), request("A".repeat(31), "Valid", facultyA)).andExpect(status().isBadRequest());
        perform(post(BASE), request("DEPT-VALID", " ", facultyA)).andExpect(status().isBadRequest());
        long id = create("DEPT-UPDATE", "Update", facultyA);
        for (String field : List.of("departmentCode", "departmentName", "facultyId", "isActive")) {
            var body = request("DEPT-UPDATE", "Update", facultyA);
            body.remove(field);
            perform(put(BASE + "/" + id), body).andExpect(status().isBadRequest());
        }
        for (String path : List.of("0", "-1", "abc", "9223372036854775808"))
            perform(get(BASE + "/" + path), null).andExpect(status().isBadRequest());
        perform(get(BASE + "/9223372036854775807"), null).andExpect(status().isNotFound());
    }

    @Test
    void listSupportsLiteralSearchFiltersAndStablePagination() throws Exception {
        long one = create("DEPT-ENG", "Engineering 100%_มาลี", facultyA);
        long two = create("DEPT-IT", "Information Technology", facultyB);
        long three = create("DEPT-INACTIVE", "Engineering Other", facultyA);
        db.update("UPDATE departments SET is_active=FALSE WHERE id=?", three);
        long gone = create("DEPT-GONE", "Engineering Gone", facultyA);
        perform(delete(BASE + "/" + gone), null).andExpect(status().isNoContent());
        perform(get(BASE), null).andExpect(status().isOk()).andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(20)).andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("sort[1]").value("id,asc"));
        perform(get(BASE).param("search", "engineering").param("facultyId", String.valueOf(facultyA)).param("status", "ACTIVE"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(one));
        for (String search : List.of("dept-eng", "100%_", "มาลี"))
            perform(get(BASE).param("search", search), null).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        perform(get(BASE).param("status", "INACTIVE"), null).andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three));
        perform(get(BASE).param("facultyId", String.valueOf(facultyB)), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].id").value(two)).andExpect(jsonPath("content[0].facultyCode").value("FAC-B"));
        perform(get(BASE).param("sort", "departmentName,asc").param("size", "1").param("page", "1"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(three))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("first").value(false)).andExpect(jsonPath("last").value(false));
        perform(get(BASE).param("page", "99"), null).andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("last").value(true)).andExpect(jsonPath("totalElements").value(3));
        perform(get(BASE).param("facultyId", String.valueOf(Long.MAX_VALUE)), null).andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(0));
        for (String query : List.of("size=0", "size=101", "size=99999999999999", "page=-1", "page=2147483647&size=100",
                "facultyId=0", "facultyId=abc", "status=active", "includeDeleted=true", "sort=faculty_name,asc",
                "sort=id,up", "sort=id,asc&sort=id,desc", "page=0&page=1"))
            perform(get(BASE + "?" + query), null).andExpect(status().isBadRequest());
    }

    @Test
    void assignedUsersPreventDeletionWithoutClearingAssignments() throws Exception {
        long id = create("DEPT-ASSIGNED", "Assigned", facultyA);
        long user = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status,department_id)
                VALUES ('ASSIGNED','Assigned','User','assigned@example.test','ACTIVE',?) RETURNING id
                """, Long.class, id);
        for (String state : List.of("ACTIVE", "INACTIVE", "LOCKED", "SUSPENDED")) {
            db.update("UPDATE app_users SET account_status=? WHERE id=?", state, user);
            perform(delete(BASE + "/" + id), null).andExpect(status().isConflict()).andExpect(jsonPath("code").value("DEPARTMENT_IN_USE"));
            assertThat(db.queryForObject("SELECT department_id FROM app_users WHERE id=?", Long.class, user)).isEqualTo(id);
            assertThat(db.queryForObject("SELECT updated_at FROM departments WHERE id=?", java.sql.Timestamp.class, id)).isNull();
        }
        db.update("UPDATE app_users SET is_deleted=TRUE WHERE id=?", user);
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT department_id FROM app_users WHERE id=?", Long.class, user)).isEqualTo(id);
        perform(post("/api/v1/admin/users"), userRequest(id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DEPARTMENT_NOT_FOUND"));
    }

    @Test
    void allAdministrativeRolesCanMutateAndCurrentAuthorityIsRequired() throws Exception {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            long id = create("DEPT-" + role.replace('_', '-'), role, facultyA);
            perform(put(BASE + "/" + id), request("DEPT-" + role.replace('_', '-'), "Updated " + role, facultyB)).andExpect(status().isOk());
            perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        }
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('DEPT_TEST_READER','Department test reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("DEPT_TEST_READER"); // Existing token still claims SUPER_ADMIN: database roles must win.
        for (var route : List.of(get(BASE), get(BASE + "/summary"), get(BASE + "/1"), delete(BASE + "/1")))
            perform(route, null).andExpect(status().isForbidden());
        perform(post(BASE), request("DEPT-NO", "Denied", facultyA)).andExpect(status().isForbidden());
        setRole("SUPER_ADMIN");
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        perform(get(BASE), null).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        perform(post(BASE), request("DEPT-NO", "Denied", facultyA)).andExpect(status().isForbidden());
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
        assertThatThrownBy(() -> departments.summary(principal)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> departments.delete(principal, 1)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> departments.create(principal, new DepartmentCreateRequest())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void duplicateCodeRaceAcrossDifferentFacultiesIsAtomic() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var first = pool.submit(() -> { barrier.await(); return perform(post(BASE), request("DEPT-RACE", "Race A", facultyA)).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { barrier.await(); return perform(post(BASE), request("DEPT-RACE", "Race B", facultyB)).andReturn().getResponse().getStatus(); });
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM departments WHERE department_code='DEPT-RACE'", Integer.class)).isOne();
    }

    @Test
    void userAssignmentRacingDeletionNeverLeavesLiveUserOnDeletedDepartment() throws Exception {
        long id = create("DEPT-ASSIGN-RACE", "Race", facultyA);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            var first = pool.submit(() -> { barrier.await(); return perform(post("/api/v1/admin/users"), userRequest(id)).andReturn().getResponse().getStatus(); });
            var second = pool.submit(() -> { barrier.await(); return perform(delete(BASE + "/" + id), null).andReturn().getResponse().getStatus(); });
            int assignment = first.get(15, TimeUnit.SECONDS), deletion = second.get(15, TimeUnit.SECONDS);
            assertThat((assignment == 201 && deletion == 409) || (assignment == 400 && deletion == 204)).isTrue();
        }
        assertThat(db.queryForObject("SELECT count(*) FROM app_users u JOIN departments d ON d.id=u.department_id WHERE NOT u.is_deleted AND d.is_deleted", Integer.class)).isZero();
    }

    private void facultyCount(long id, int expected) throws Exception {
        perform(get("/api/v1/admin/faculties/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("departmentCount").value(expected));
    }
    private void setRole(String code) {
        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", actorId, code);
    }
    private long faculty(String code, String name) {
        return db.queryForObject("INSERT INTO faculties(faculty_code,faculty_name_en,created_by) VALUES (?,?,?) RETURNING id",
                Long.class, code, name, actorId);
    }
    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", "department.actor@example.test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }
    private long create(String code, String name, long faculty) throws Exception {
        return tree(perform(post(BASE), request(code, name, faculty)).andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }
    private Map<String, Object> request(String code, String name, long faculty) {
        return new LinkedHashMap<>(Map.of("departmentCode", code, "departmentName", name, "facultyId", faculty, "isActive", true));
    }
    private Map<String, Object> userRequest(long departmentId) {
        long role = db.queryForObject("SELECT id FROM app_roles WHERE role_code='ADMIN'", Long.class);
        return Map.of("employeeId", "RACING-USER", "firstName", "Racing", "lastName", "User", "email", "racing@example.test",
                "roleId", role, "departmentId", departmentId, "temporaryPassword", "temporary department password",
                "confirmPassword", "temporary department password");
    }
    private ResultActions perform(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        builder.header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(builder);
    }
    private JsonNode tree(MvcResult result) { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
