package com.smartAiUniversityAssistant.seniorproject.feature.user;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.LoginRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.CreateAdminUserRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.user.service.AdminUserService;
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
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class AdminUserIntegrationTests extends IntegrationSupport {
    private static final String USERS = "/api/v1/admin/users";
    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String ACTOR_PASSWORD = "super admin test password";
    private static final String TEMPORARY_PASSWORD = "temporary passphrase 2026";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired AdminUserService adminUsers;
    private long actorId;

    @BeforeEach
    void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("UPDATE app_users SET department_id=NULL");
        db.update("DELETE FROM programs");
        db.update("DELETE FROM departments");
        db.update("DELETE FROM faculties");
        db.update("UPDATE app_users SET created_by=NULL,updated_by=NULL");
        db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('ACTOR-01','Super','Admin','super@example.test','ACTIVE') RETURNING id
                """, Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(ACTOR_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='SUPER_ADMIN'", actorId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminCreatesCompleteAccountWithSafeDefaultsAndNoNewSession() throws Exception {
        String access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        long roleId = roleId("ADMIN");
        Map<String, Object> request = validRequest(roleId, "NEW-01", "New.User@example.test");
        request.remove("accountStatus");
        request.remove("forcePasswordChange");
        request.put("phoneNumber", "  +66 81 234 5678  ");
        request.put("firstName", "  มาลี  ");

        mvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("employeeId").value("NEW-01"))
                .andExpect(jsonPath("firstName").value("มาลี"))
                .andExpect(jsonPath("phoneNumber").value("+66 81 234 5678"))
                .andExpect(jsonPath("email").value("New.User@example.test"))
                .andExpect(jsonPath("departmentId").doesNotExist())
                .andExpect(jsonPath("accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("roles[0].id").value(roleId))
                .andExpect(jsonPath("roles[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("forcePasswordChange").value(true))
                .andExpect(jsonPath("createdBy").value(actorId))
                .andExpect(jsonPath("createdAt").value(clock.instant().toString()))
                .andExpect(jsonPath("passwordHash").doesNotExist())
                .andExpect(jsonPath("temporaryPassword").doesNotExist());

        Long userId = db.queryForObject("SELECT id FROM app_users WHERE employee_id='NEW-01'", Long.class);
        assertThat(db.queryForMap("SELECT phone_number,department_id,account_status,is_deleted,created_by,updated_by,updated_at FROM app_users WHERE id=?", userId))
                .containsEntry("phone_number", "+66 81 234 5678")
                .containsEntry("account_status", "ACTIVE")
                .containsEntry("is_deleted", false)
                .containsEntry("created_by", actorId);
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_roles WHERE user_id=? AND role_id=? AND assigned_by=?",
                Integer.class, userId, roleId, actorId)).isOne();
        Map<String, Object> credential = db.queryForMap("SELECT password_hash,force_password_change,failed_login_attempts,password_changed_at,locked_until,updated_at FROM appuser_credentials WHERE user_id=?", userId);
        assertThat(encoder.matches(TEMPORARY_PASSWORD, (String) credential.get("password_hash"))).isTrue();
        assertThat(credential).containsEntry("force_password_change", true).containsEntry("failed_login_attempts", 0);
        assertThat(redis.keys("*:session:{" + userId + "}:*")).isEmpty();

        JsonNode newLogin = login("New.User@example.test", TEMPORARY_PASSWORD);
        assertThat(newLogin.get("forcePasswordChange").asBoolean()).isTrue();
    }

    @Test
    void explicitValuesPersistAndActiveUserCanLoginWithoutRestriction() throws Exception {
        String access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        long departmentId = insertDepartment("DEPT-EXPL", "Explicit Department");
        Map<String, Object> request = validRequest(roleId("ACADEMIC_ADMIN"), "NEW-02", "normal@example.test");
        request.put("departmentId", departmentId);
        request.put("forcePasswordChange", false);
        create(access, request).andExpect(status().isCreated())
                .andExpect(jsonPath("departmentId").value(departmentId))
                .andExpect(jsonPath("forcePasswordChange").value(false));
        assertThat(login("normal@example.test", TEMPORARY_PASSWORD).get("forcePasswordChange").asBoolean()).isFalse();
    }

    @Test
    void strictValidationRejectsSecretsUnknownFieldsNullDefaultsAndWrongTypes() throws Exception {
        String access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        Map<String, Object> request = validRequest(roleId("ADMIN"), "BAD-01", "bad@example.test");
        request.put("confirmPassword", "different password value");
        create(access, request).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(TEMPORARY_PASSWORD))));

        request = validRequest(roleId("ADMIN"), "BAD-02", "bad2@example.test");
        request.put("createdBy", actorId);
        create(access, request).andExpect(status().isBadRequest());
        request = validRequest(roleId("ADMIN"), "BAD-03", "bad3@example.test");
        request.put("accountStatus", null);
        create(access, request).andExpect(status().isBadRequest());
        request = validRequest(roleId("ADMIN"), "BAD-04", "bad4@example.test");
        request.put("forcePasswordChange", null);
        create(access, request).andExpect(status().isBadRequest());
        request = validRequest(roleId("ADMIN"), "BAD-05", "bad5@example.test");
        request.put("roleId", Long.toString(roleId("ADMIN")));
        create(access, request).andExpect(status().isBadRequest());
        request = validRequest(roleId("ADMIN"), "BAD-06", "bad6@example.test");
        request.put("phoneNumber", "not-a-phone");
        create(access, request).andExpect(status().isBadRequest());

        mvc.perform(post(USERS).contentType(MediaType.TEXT_PLAIN).header("Authorization", "Bearer " + access).content("invalid"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON).header("Authorization", "Bearer " + access)
                        .content("{\"padding\":\"" + "x".repeat(17000) + "\"}"))
                .andExpect(status().isContentTooLarge()).andExpect(jsonPath("code").value("CONTENT_TOO_LARGE"));
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE employee_id LIKE 'BAD-%'", Integer.class)).isZero();
    }

    @Test
    void authorizationAndForcedChangeFailBeforeAccountChecks() throws Exception {
        Map<String, Object> request = validRequest(999999L, "AUTH-01", "auth@example.test");
        create(null, request).andExpect(status().isUnauthorized());

        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='ADMIN'", actorId);
        String adminAccess = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        create(adminAccess, request).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ACCESS_DENIED"));

        fixture();
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        String restricted = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        create(restricted, request).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE employee_id='AUTH-01'", Integer.class)).isZero();
    }

    @Test
    void methodSecurityRejectsDirectNonSuperAdminInvocation() {
        var principal = new AuthenticatedUser(actorId, "session", "revision", false, Set.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(() -> adminUsers.create(principal, requestObject(roleId("ADMIN"), "DIRECT-01", "direct@example.test")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void roleAndDuplicateFailuresAreSpecificAndLeaveNoPartialRows() throws Exception {
        String access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        long roleId = roleId("ADMIN");
        create(access, validRequest(roleId, "DUP-01", "duplicate@example.test")).andExpect(status().isCreated());
        create(access, validRequest(roleId, "DUP-02", "duplicate@example.test")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("EMAIL_ALREADY_EXISTS"));
        create(access, validRequest(roleId, "DUP-01", "other@example.test")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("EMPLOYEE_ID_ALREADY_EXISTS"));
        create(access, validRequest(roleId, "DUP-01", "duplicate@example.test")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("USER_ALREADY_EXISTS"));

        db.update("UPDATE app_roles SET is_active=FALSE WHERE id=?", roleId);
        create(access, validRequest(roleId, "ROLE-01", "role@example.test")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("ROLE_INACTIVE"));
        create(access, validRequest(999999L, "ROLE-02", "missing@example.test")).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("ROLE_NOT_FOUND"));
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE employee_id LIKE 'ROLE-%'", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE employee_id LIKE 'DUP-%'", Integer.class)).isOne();
    }

    @Test
    void concurrentDuplicateRequestsCreateOneCompleteAccount() throws Exception {
        String access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        Map<String, Object> request = validRequest(roleId("ADMIN"), "RACE-01", "race@example.test");
        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> attempt = () -> {
                barrier.await();
                return create(access, request).andReturn().getResponse().getStatus();
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(201, 409);
        }
        Long id = db.queryForObject("SELECT id FROM app_users WHERE employee_id='RACE-01'", Long.class);
        assertThat(db.queryForObject("SELECT count(*) FROM appuser_credentials WHERE user_id=?", Integer.class, id)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_roles WHERE user_id=?", Integer.class, id)).isOne();
    }

    @Test
    void missingDepartmentRejectsCreationWithoutPartialAccount() throws Exception {
        String access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        Map<String, Object> request = validRequest(roleId("ADMIN"), "DEPT-MISSING", "dept.missing@example.test");
        request.put("departmentId", Long.MAX_VALUE);
        create(access, request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DEPARTMENT_NOT_FOUND"));
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE employee_id='DEPT-MISSING'", Integer.class)).isZero();
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions create(String access, Object request) throws Exception {
        var builder = post(USERS).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request));
        if (access != null) builder.header("Authorization", "Bearer " + access);
        return mvc.perform(builder);
    }

    private long roleId(String code) {
        return db.queryForObject("SELECT id FROM app_roles WHERE role_code=?", Long.class, code);
    }

    private long insertDepartment(String code, String name) {
        long facultyId = db.queryForObject("""
                INSERT INTO faculties(faculty_code,faculty_name_en,is_active,is_deleted,created_by,created_at)
                VALUES (?,?,TRUE,FALSE,?,CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, "FAC-" + code, "Faculty for " + name, actorId);
        return db.queryForObject("""
                INSERT INTO departments(department_code,department_name,faculty_id,is_active,is_deleted,created_by,created_at)
                VALUES (?,?,?,TRUE,FALSE,?,CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, code, name, facultyId, actorId);
    }

    private Map<String, Object> validRequest(long roleId, String employeeId, String email) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("employeeId", employeeId);
        request.put("firstName", "Test");
        request.put("lastName", "User");
        request.put("email", email);
        request.put("accountStatus", "ACTIVE");
        request.put("roleId", roleId);
        request.put("temporaryPassword", TEMPORARY_PASSWORD);
        request.put("confirmPassword", TEMPORARY_PASSWORD);
        request.put("forcePasswordChange", true);
        return request;
    }

    private CreateAdminUserRequest requestObject(long roleId, String employeeId, String email) {
        var request = new CreateAdminUserRequest();
        request.setEmployeeId(employeeId);
        request.setFirstName("Test");
        request.setLastName("User");
        request.setEmail(email);
        request.setRoleId(roleId);
        request.setTemporaryPassword(TEMPORARY_PASSWORD);
        request.setConfirmPassword(TEMPORARY_PASSWORD);
        return request;
    }
}
