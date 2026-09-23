package com.smartAiUniversityAssistant.seniorproject.feature.user;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.LoginRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request.*;
import com.smartAiUniversityAssistant.seniorproject.feature.user.service.AdminUserService;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.Instant;
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
class AdminUserCrudIntegrationTests extends IntegrationSupport {
    private static final String USERS = "/api/v1/admin/users";
    private static final String LOGIN = "/api/v1/admin/auth/login";
    private static final String ME = "/api/v1/admin/auth/me";
    private static final String ACTOR_PASSWORD = "super admin test password";
    private static final String TARGET_PASSWORD = "target account password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired AdminUserService adminUsers;
    private long actorId;
    private String access;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        db.update("DELETE FROM app_user_roles");
        db.update("DELETE FROM appuser_credentials");
        db.update("UPDATE app_users SET created_by=NULL,updated_by=NULL");
        db.update("DELETE FROM app_users");
        db.update("UPDATE app_roles SET is_active=TRUE");
        db.update("DELETE FROM app_roles WHERE role_code='REGISTRAR'");
        actorId = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status,created_at)
                VALUES ('ACTOR-01','Super','Admin','super@example.test','ACTIVE',?) RETURNING id
                """, Long.class, Timestamp.valueOf("2026-01-01 00:00:00"));
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)",
                actorId, encoder.encode(ACTOR_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id,assigned_at) SELECT ?,id,? FROM app_roles WHERE role_code='SUPER_ADMIN'",
                actorId, Timestamp.valueOf("2026-01-01 00:00:00"));
        access = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listAppliesDefaultsStableSortAndSafeProjection() throws Exception {
        long admin = roleId("ADMIN"), superAdmin = roleId("SUPER_ADMIN");
        long first = insertUser("LIST-01", "A", "One", "list1@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        long multi = insertUser("LIST-02", "B", "Two", "list2@example.test", "INACTIVE", null, "2026-01-01 02:00:00");
        long zeroRole = insertUser("LIST-03", "C", "Three", "list3@example.test", "SUSPENDED", null, "2026-01-01 03:00:00");
        assign(first, "ADMIN");
        assign(multi, "SUPER_ADMIN");
        assign(multi, "ADMIN");

        getJson(USERS).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("page").value(0))
                .andExpect(jsonPath("size").value(50))
                .andExpect(jsonPath("totalElements").value(4))
                .andExpect(jsonPath("totalPages").value(1))
                .andExpect(jsonPath("first").value(true))
                .andExpect(jsonPath("last").value(true))
                .andExpect(jsonPath("sort[0]").value("createdAt,desc"))
                .andExpect(jsonPath("sort[1]").value("id,asc"))
                .andExpect(jsonPath("content[0].employeeId").value("LIST-03"))
                .andExpect(jsonPath("content[0].roles").isArray())
                .andExpect(jsonPath("content[0].forcePasswordChange").doesNotExist())
                .andExpect(jsonPath("content[0].password").doesNotExist())
                .andExpect(jsonPath("content[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("content[1].employeeId").value("LIST-02"))
                .andExpect(jsonPath("content[1].roles[0].id").value(Math.min(admin, superAdmin)))
                .andExpect(jsonPath("content[1].roles[1].id").value(Math.max(admin, superAdmin)))
                .andExpect(jsonPath("content[1].roles[0].isActive").value(true))
                .andExpect(jsonPath("content[1].roles[0].roleName").exists())
                .andExpect(jsonPath("content[1].roles[0].assignedAt").exists())
                .andExpect(jsonPath("content[2].employeeId").value("LIST-01"))
                .andExpect(jsonPath("content[3].employeeId").value("ACTOR-01"));

        String body = getJson(USERS).andReturn().getResponse().getContentAsString();
        int occurrences = body.split("\"employeeId\":\"LIST-02\"", -1).length - 1;
        assertThat(occurrences).as("multi-role user appears exactly once").isEqualTo(1);

        getJson(USERS + "?page=99&size=5").andExpect(status().isOk())
                .andExpect(jsonPath("content").isEmpty())
                .andExpect(jsonPath("totalElements").value(4))
                .andExpect(jsonPath("totalPages").value(1))
                .andExpect(jsonPath("first").value(false))
                .andExpect(jsonPath("last").value(true));
    }

    @Test
    void listQueryValidationRejectsBadInput() throws Exception {
        getJson(USERS + "?unknown=1").andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        getJson(USERS + "?page=-1").andExpect(status().isBadRequest());
        getJson(USERS + "?page=99999999999999999999").andExpect(status().isBadRequest());
        getJson(USERS + "?size=0").andExpect(status().isBadRequest());
        getJson(USERS + "?size=51").andExpect(status().isBadRequest());
        getJson(USERS + "?size=101").andExpect(status().isBadRequest());
        getJson(USERS + "?size=50").andExpect(status().isOk());
        getJson(USERS + "?accountStatus=active").andExpect(status().isBadRequest());
        getJson(USERS + "?roleId=abc").andExpect(status().isBadRequest());
        getJson(USERS + "?roleId=0").andExpect(status().isBadRequest());
        getJson(USERS + "?departmentId=-2").andExpect(status().isBadRequest());
        getJson(USERS + "?departmentUnassigned=yes").andExpect(status().isBadRequest());
        getJson(USERS + "?departmentId=1&departmentUnassigned=true").andExpect(status().isBadRequest());
        getJson(USERS + "?sort=unknown,asc").andExpect(status().isBadRequest());
        getJson(USERS + "?sort=createdAt,up").andExpect(status().isBadRequest());
        getJson(USERS + "?sort=createdAt").andExpect(status().isBadRequest());
        getJson(USERS + "?sort=createdAt,desc&sort=createdAt,asc").andExpect(status().isBadRequest());
        getJson(USERS + "?sort=id,asc&sort=email,asc&sort=firstName,asc&sort=lastName,asc").andExpect(status().isBadRequest());
    }

    @Test
    void listSearchIsLiteralCaseInsensitiveAndFiltersCombine() throws Exception {
        long admin = roleId("ADMIN");
        long one = insertUser("SRCH-01", "Mali", "One", "mali.s@example.test", "ACTIVE", 5L, "2026-01-01 01:00:00");
        long two = insertUser("SRCH-02", "Malik", "Two", "special%user@example.test", "INACTIVE", null, "2026-01-01 02:00:00");
        insertUser("SRCH-03", "Noi", "Three", "noi@example.test", "LOCKED", 7L, "2026-01-01 03:00:00");
        assign(one, "ADMIN");
        assign(two, "SUPER_ADMIN");

        mvc.perform(get(USERS).param("search", "mali").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get(USERS).param("search", "MALI").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get(USERS).param("search", "special%user").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].email").value("special%user@example.test"));
        mvc.perform(get(USERS).param("search", "%").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get(USERS).param("search", "mali").param("accountStatus", "ACTIVE")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get(USERS).param("roleId", String.valueOf(admin)).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        mvc.perform(get(USERS).param("departmentId", "7").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].employeeId").value("SRCH-03"));
        mvc.perform(get(USERS).param("departmentUnassigned", "true").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
        mvc.perform(get(USERS).param("roleId", "999999").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(0))
                .andExpect(jsonPath("totalPages").value(0))
                .andExpect(jsonPath("last").value(true));
        mvc.perform(get(USERS).param("search", "  มาลี  ").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
    }

    @Test
    void listIncludesInactiveRolesAndZeroRoleUsers() throws Exception {
        long admin = roleId("ADMIN");
        long inactiveHolder = insertUser("INACT-01", "Inactive", "Holder", "inactive@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        assign(inactiveHolder, "ADMIN");
        db.update("UPDATE app_roles SET is_active=FALSE WHERE role_code='ADMIN'");

        mvc.perform(get(USERS).param("roleId", String.valueOf(admin)).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].roles[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("content[0].roles[0].isActive").value(false));
    }

    @Test
    void detailReturnsSafeProjectionAndNotFoundCases() throws Exception {
        long target = insertUser("DET-01", "Detail", "User", "detail@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        assign(target, "ACADEMIC_ADMIN");
        long deleted = insertUser("DET-02", "Gone", "User", "gone@example.test", "ACTIVE", null, "2026-01-01 02:00:00");
        db.update("UPDATE app_users SET is_deleted=TRUE WHERE id=?", deleted);

        getJson(USERS + "/" + target).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("id").value(target))
                .andExpect(jsonPath("employeeId").value("DET-01"))
                .andExpect(jsonPath("createdBy").doesNotExist())
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("roles[0].roleCode").value("ACADEMIC_ADMIN"))
                .andExpect(jsonPath("forcePasswordChange").doesNotExist());
        getJson(USERS + "/999999").andExpect(status().isNotFound()).andExpect(jsonPath("code").value("USER_NOT_FOUND"));
        getJson(USERS + "/" + deleted).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("USER_NOT_FOUND"));
        getJson(USERS + "/abc").andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        getJson(USERS + "/-5").andExpect(status().isBadRequest());
    }

    @Test
    void fullUpdateAppliesAtomicallyWithAuditAndEmailLogin() throws Exception {
        long target = insertUser("UPD-01", "Before", "User", "before@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        addCredentials(target, TARGET_PASSWORD, false);
        assign(target, "ADMIN");

        Map<String, Object> request = updateRequest(roleId("SUPER_ADMIN"), "UPD-02", "after@example.test");
        request.put("firstName", "  After  ");
        request.put("lastName", "Changed");
        request.put("phoneNumber", "  +66 91 111 2222  ");
        request.put("departmentId", 7L);
        putJson(USERS + "/" + target, request).andExpect(status().isOk())
                .andExpect(jsonPath("employeeId").value("UPD-02"))
                .andExpect(jsonPath("firstName").value("After"))
                .andExpect(jsonPath("phoneNumber").value("+66 91 111 2222"))
                .andExpect(jsonPath("departmentId").value(7))
                .andExpect(jsonPath("roles[0].roleCode").value("SUPER_ADMIN"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("forcePasswordChange").doesNotExist());

        Map<String, Object> row = db.queryForMap("SELECT created_by, created_at FROM app_users WHERE id=?", target);
        assertThat(row.get("created_by")).isNull();
        assertThat(row.get("created_at").toString()).startsWith("2026-01-01 01:00");
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_roles WHERE user_id=?", Integer.class, target)).isOne();

        assertThat(login("after@example.test", TARGET_PASSWORD).has("accessToken")).isTrue();
        assertThat(login("before@example.test", TARGET_PASSWORD).get("message").asText()).isNotBlank();
    }

    @Test
    void fullUpdateValidationMissingKeysAndNoop() throws Exception {
        long target = insertUser("VAL-01", "Valid", "User", "valid@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        assign(target, "ADMIN");
        Map<String, Object> base = updateRequest(roleId("ADMIN"), "VAL-01", "valid@example.test");

        Map<String, Object> missingPhone = new LinkedHashMap<>(base);
        missingPhone.remove("phoneNumber");
        putJson(USERS + "/" + target, missingPhone).andExpect(status().isBadRequest());
        Map<String, Object> missingDepartment = new LinkedHashMap<>(base);
        missingDepartment.remove("departmentId");
        putJson(USERS + "/" + target, missingDepartment).andExpect(status().isBadRequest());
        Map<String, Object> nullFirstName = new LinkedHashMap<>(base);
        nullFirstName.put("firstName", null);
        putJson(USERS + "/" + target, nullFirstName).andExpect(status().isBadRequest());
        Map<String, Object> unknown = new LinkedHashMap<>(base);
        unknown.put("isDeleted", false);
        putJson(USERS + "/" + target, unknown).andExpect(status().isBadRequest());
        Map<String, Object> credential = new LinkedHashMap<>(base);
        credential.put("password", "any password value");
        putJson(USERS + "/" + target, credential).andExpect(status().isBadRequest());
        Map<String, Object> stringRole = new LinkedHashMap<>(base);
        stringRole.put("roleId", String.valueOf(roleId("ADMIN")));
        putJson(USERS + "/" + target, stringRole).andExpect(status().isBadRequest());
        Map<String, Object> badPhone = new LinkedHashMap<>(base);
        badPhone.put("phoneNumber", "not-a-phone");
        putJson(USERS + "/" + target, badPhone).andExpect(status().isBadRequest());

        Map<String, Object> noop = updateRequest(roleId("ADMIN"), "VAL-01", "valid@example.test");
        noop.put("firstName", "Valid");
        noop.put("lastName", "User");
        putJson(USERS + "/" + target, noop).andExpect(status().isOk())
                .andExpect(jsonPath("updatedBy").doesNotExist())
                .andExpect(jsonPath("updatedAt").doesNotExist());
        assertThat(db.queryForObject("SELECT updated_at FROM app_users WHERE id=?", Timestamp.class, target)).isNull();
    }

    @Test
    void fullUpdateDuplicatesIncludeDeletedAndLeaveNoPartialState() throws Exception {
        insertUser("DUPA-01", "Other", "Live", "other.live@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        long deleted = insertUser("DUPB-01", "Other", "Gone", "other.gone@example.test", "ACTIVE", null, "2026-01-01 02:00:00");
        db.update("UPDATE app_users SET is_deleted=TRUE WHERE id=?", deleted);
        long target = insertUser("DUPC-01", "Target", "User", "target@example.test", "ACTIVE", null, "2026-01-01 03:00:00");
        assign(target, "ADMIN");

        Map<String, Object> emailClash = updateRequest(roleId("ADMIN"), "DUPC-01", "other.live@example.test");
        putJson(USERS + "/" + target, emailClash).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("EMAIL_ALREADY_EXISTS"));
        Map<String, Object> employeeClash = updateRequest(roleId("ADMIN"), "DUPA-01", "target@example.test");
        putJson(USERS + "/" + target, employeeClash).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("EMPLOYEE_ID_ALREADY_EXISTS"));
        Map<String, Object> bothClash = updateRequest(roleId("ADMIN"), "DUPA-01", "other.live@example.test");
        putJson(USERS + "/" + target, bothClash).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("USER_ALREADY_EXISTS"));
        Map<String, Object> deletedClash = updateRequest(roleId("ADMIN"), "DUPC-01", "other.gone@example.test");
        putJson(USERS + "/" + target, deletedClash).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("EMAIL_ALREADY_EXISTS"));

        Map<String, Object> unchanged = updateRequest(roleId("ADMIN"), "DUPC-01", "target@example.test");
        unchanged.put("firstName", "Target");
        putJson(USERS + "/" + target, unchanged).andExpect(status().isOk());

        assertThat(db.queryForObject("SELECT first_name FROM app_users WHERE id=?", String.class, target)).isEqualTo("Target");
        assertThat(db.queryForObject("SELECT email FROM app_users WHERE id=?", String.class, target)).isEqualTo("target@example.test");
    }

    @Test
    void statusTransitionsNoopAndCredentialStatePreserved() throws Exception {
        long target = insertUser("STAT-01", "Status", "User", "status@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        addCredentials(target, TARGET_PASSWORD, false);
        assign(target, "ADMIN");
        String targetAccess = login("status@example.test", TARGET_PASSWORD).get("accessToken").asText();
        Map<String, Object> before = db.queryForMap(
                "SELECT password_hash,failed_login_attempts,locked_until,force_password_change FROM appuser_credentials WHERE user_id=?", target);

        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("accountStatus").value("INACTIVE"))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));

        login("status@example.test", TARGET_PASSWORD);
        mvc.perform(get(ME).header("Authorization", "Bearer " + targetAccess)).andExpect(status().isUnauthorized());

        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "LOCKED")).andExpect(status().isOk());
        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "SUSPENDED")).andExpect(status().isOk());
        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "ACTIVE")).andExpect(status().isOk());
        assertThat(login("status@example.test", TARGET_PASSWORD).has("accessToken")).isTrue();
        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "BROKEN")).andExpect(status().isBadRequest());

        Map<String, Object> after = db.queryForMap(
                "SELECT password_hash,failed_login_attempts,locked_until,force_password_change FROM appuser_credentials WHERE user_id=?", target);
        assertThat(after.get("password_hash")).isEqualTo(before.get("password_hash"));
        assertThat(after.get("force_password_change")).isEqualTo(before.get("force_password_change"));
        assertThat(after.get("locked_until")).isNull();

        db.update("UPDATE app_users SET account_status='BROKEN',updated_at=NULL,updated_by=NULL WHERE id=?", target);
        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("accountStatus").value("ACTIVE"));
    }

    @Test
    void roleReplacementProducesExactOneSetWithTrustedAudit() throws Exception {
        long admin = roleId("ADMIN"), academic = roleId("ACADEMIC_ADMIN"), superAdmin = roleId("SUPER_ADMIN");
        long marker = insertUser("MARKER-01", "Marker", "User", "marker@example.test", "ACTIVE", null, "2026-01-01 00:30:00");
        long target = insertUser("ROLE-01", "Role", "User", "role.user@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        assign(target, "ADMIN", marker, "2025-05-05 05:05:05");
        assign(target, "ACADEMIC_ADMIN", marker, "2025-06-06 06:06:06");

        putJson(USERS + "/" + target + "/role", Map.of("roleId", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("roles.length()").value(1))
                .andExpect(jsonPath("roles[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("roles[0].assignedBy").value(marker))
                .andExpect(jsonPath("updatedBy").value(actorId))
                .andExpect(jsonPath("updatedAt").value(clock.instant().toString()));
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_roles WHERE user_id=?", Integer.class, target)).isOne();

        clock.advance(java.time.Duration.ofMinutes(5));
        putJson(USERS + "/" + target + "/role", Map.of("roleId", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("updatedAt").value(clock.instant().minus(java.time.Duration.ofMinutes(5)).toString()));

        putJson(USERS + "/" + target + "/role", Map.of("roleId", superAdmin)).andExpect(status().isOk())
                .andExpect(jsonPath("roles[0].roleCode").value("SUPER_ADMIN"))
                .andExpect(jsonPath("roles[0].assignedBy").value(actorId))
                .andExpect(jsonPath("roles[0].assignedAt").value(clock.instant().toString()));

        putJson(USERS + "/" + target + "/role", Map.of("roleId", 999999)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ROLE"));
        db.update("UPDATE app_roles SET is_active=FALSE WHERE id=?", academic);
        putJson(USERS + "/" + target + "/role", Map.of("roleId", academic)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ROLE"));
        db.update("INSERT INTO app_roles(role_code,role_name,is_active,created_at) VALUES ('REGISTRAR','Registrar',TRUE,CURRENT_TIMESTAMP)");
        long registrar = roleId("REGISTRAR");
        putJson(USERS + "/" + target + "/role", Map.of("roleId", registrar)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ROLE"));
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_roles WHERE user_id=?", Integer.class, target)).isOne();
    }

    @Test
    void selfProtectionBlocksOwnStatusRoleAndDelete() throws Exception {
        long admin = roleId("ADMIN");
        patchJson(USERS + "/" + actorId + "/status", Map.of("accountStatus", "INACTIVE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SELF_ACCOUNT_CHANGE_NOT_ALLOWED"));
        putJson(USERS + "/" + actorId + "/role", Map.of("roleId", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SELF_ACCOUNT_CHANGE_NOT_ALLOWED"));
        deleteJson(USERS + "/" + actorId)
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SELF_ACCOUNT_CHANGE_NOT_ALLOWED"));

        Map<String, Object> selfProfile = updateRequest(roleId("SUPER_ADMIN"), "ACTOR-01", "super@example.test");
        selfProfile.put("firstName", "Renamed");
        putJson(USERS + "/" + actorId, selfProfile).andExpect(status().isOk())
                .andExpect(jsonPath("firstName").value("Renamed"))
                .andExpect(jsonPath("roles[0].roleCode").value("SUPER_ADMIN"));

        Map<String, Object> selfStatusChange = new LinkedHashMap<>(selfProfile);
        selfStatusChange.put("accountStatus", "INACTIVE");
        putJson(USERS + "/" + actorId, selfStatusChange).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("SELF_ACCOUNT_CHANGE_NOT_ALLOWED"));

        assertThat(db.queryForObject("SELECT account_status FROM app_users WHERE id=?", String.class, actorId)).isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT is_deleted FROM app_users WHERE id=?", Boolean.class, actorId)).isFalse();
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_roles WHERE user_id=?", Integer.class, actorId)).isOne();
    }

    @Test
    void softDeleteIsIdempotentExcludesAndReservesIdentifiers() throws Exception {
        long target = insertUser("DEL-01", "Delete", "User", "delete@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        addCredentials(target, TARGET_PASSWORD, false);
        assign(target, "ADMIN");
        assign(target, "ACADEMIC_ADMIN");
        String targetAccess = login("delete@example.test", TARGET_PASSWORD).get("accessToken").asText();

        deleteJson(USERS + "/" + target).andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        assertThat(db.queryForObject("SELECT is_deleted FROM app_users WHERE id=?", Boolean.class, target)).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_roles WHERE user_id=?", Integer.class, target)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM appuser_credentials WHERE user_id=?", Integer.class, target)).isOne();
        assertThat(db.queryForObject("SELECT updated_by FROM app_users WHERE id=?", Long.class, target)).isEqualTo(actorId);
        Timestamp deletedAt = db.queryForObject("SELECT updated_at FROM app_users WHERE id=?", Timestamp.class, target);

        login("delete@example.test", TARGET_PASSWORD);
        mvc.perform(get(ME).header("Authorization", "Bearer " + targetAccess)).andExpect(status().isUnauthorized());

        getJson(USERS + "/" + target).andExpect(status().isNotFound());
        putJson(USERS + "/" + target, updateRequest(roleId("ADMIN"), "DEL-01", "delete@example.test")).andExpect(status().isNotFound());
        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "ACTIVE")).andExpect(status().isNotFound());
        putJson(USERS + "/" + target + "/role", Map.of("roleId", roleId("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get(USERS).param("search", "delete@example").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));

        clock.advance(java.time.Duration.ofMinutes(3));
        deleteJson(USERS + "/" + target).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT updated_at FROM app_users WHERE id=?", Timestamp.class, target)).isEqualTo(deletedAt);
        deleteJson(USERS + "/999999").andExpect(status().isNotFound()).andExpect(jsonPath("code").value("USER_NOT_FOUND"));

        Map<String, Object> recreate = new LinkedHashMap<>();
        recreate.put("employeeId", "DEL-01");
        recreate.put("firstName", "New");
        recreate.put("lastName", "User");
        recreate.put("email", "delete@example.test");
        recreate.put("accountStatus", "ACTIVE");
        recreate.put("roleId", roleId("ADMIN"));
        recreate.put("temporaryPassword", "another temporary password");
        recreate.put("confirmPassword", "another temporary password");
        mvc.perform(post(USERS).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(recreate)))
                .andExpect(status().isConflict());
    }

    @Test
    void authorizationCoversEveryCrudRoute() throws Exception {
        long target = insertUser("AUTHZ-01", "Authz", "User", "authz@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        Map<String, Object> update = updateRequest(roleId("ADMIN"), "AUTHZ-01", "authz@example.test");
        mvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        mvc.perform(get(USERS + "/" + target)).andExpect(status().isUnauthorized());
        mvc.perform(put(USERS + "/" + target).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(update)))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(USERS + "/" + target + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("accountStatus", "INACTIVE")))).andExpect(status().isUnauthorized());
        mvc.perform(put(USERS + "/" + target + "/role").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("roleId", roleId("ADMIN"))))).andExpect(status().isUnauthorized());
        mvc.perform(delete(USERS + "/" + target)).andExpect(status().isUnauthorized());

        db.update("DELETE FROM app_user_roles WHERE user_id=?", actorId);
        db.update("INSERT INTO app_user_roles(user_id,role_id,assigned_at) SELECT ?,id,CURRENT_TIMESTAMP FROM app_roles WHERE role_code='ADMIN'", actorId);
        String adminAccess = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        mvc.perform(get(USERS).header("Authorization", "Bearer " + adminAccess)).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCESS_DENIED"));
        mvc.perform(delete(USERS + "/" + target).header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ACCESS_DENIED"));

        fixture();
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", actorId);
        String restricted = login("super@example.test", ACTOR_PASSWORD).get("accessToken").asText();
        mvc.perform(get(USERS).header("Authorization", "Bearer " + restricted)).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void methodSecurityRejectsDirectNonSuperAdminInvocation() {
        var principal = new AuthenticatedUser(actorId, "session", "revision", false, Set.of("ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        var query = new AdminUserListQuery(null, null, null, null, null, null, null, List.of(), Set.of());
        assertThatThrownBy(() -> adminUsers.list(principal, query)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUsers.detail(principal, 1L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUsers.updateStatus(principal, 1L, new UpdateAdminUserStatusRequest("INACTIVE")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUsers.updateRole(principal, 1L, new UpdateAdminUserRoleRequest(1L)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> adminUsers.delete(principal, 1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concurrentConflictingUpdatesPreserveConstraintAndAtomicity() throws Exception {
        long first = insertUser("RACE-A", "First", "User", "race.a@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        long second = insertUser("RACE-B", "Second", "User", "race.b@example.test", "ACTIVE", null, "2026-01-01 02:00:00");
        assign(first, "ADMIN");
        assign(second, "ADMIN");
        Map<String, Object> bodyA = updateRequest(roleId("ADMIN"), "RACE-A", "race.same@example.test");
        bodyA.put("firstName", "FirstWon");
        Map<String, Object> bodyB = updateRequest(roleId("ADMIN"), "RACE-B", "race.same@example.test");
        bodyB.put("firstName", "SecondWon");

        try (var pool = Executors.newFixedThreadPool(2)) {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> attemptA = () -> { barrier.await(); return putJson(USERS + "/" + first, bodyA).andReturn().getResponse().getStatus(); };
            Callable<Integer> attemptB = () -> { barrier.await(); return putJson(USERS + "/" + second, bodyB).andReturn().getResponse().getStatus(); };
            var resultA = pool.submit(attemptA);
            var resultB = pool.submit(attemptB);
            assertThat(List.of(resultA.get(), resultB.get())).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE email='race.same@example.test'", Integer.class)).isOne();
        String loserFirstName = db.queryForObject(
                "SELECT first_name FROM app_users WHERE email<>'race.same@example.test' AND employee_id LIKE 'RACE-%'", String.class);
        assertThat(loserFirstName).isIn("First", "Second");
    }

    @Test
    void crudRoutesNeverCreateSessionsOrExposeSecrets() throws Exception {
        long target = insertUser("SEC-01", "Secret", "User", "secret@example.test", "ACTIVE", null, "2026-01-01 01:00:00");
        addCredentials(target, TARGET_PASSWORD, false);
        assign(target, "ADMIN");
        Set<String> before = redis.keys("susa:test:auth:session:*");

        String listBody = getJson(USERS).andReturn().getResponse().getContentAsString();
        String detailBody = getJson(USERS + "/" + target).andReturn().getResponse().getContentAsString();
        String updateBody = putJson(USERS + "/" + target,
                updateRequest(roleId("ADMIN"), "SEC-01", "secret@example.test")).andReturn().getResponse().getContentAsString();
        patchJson(USERS + "/" + target + "/status", Map.of("accountStatus", "INACTIVE")).andExpect(status().isOk());
        putJson(USERS + "/" + target + "/role", Map.of("roleId", roleId("ACADEMIC_ADMIN"))).andExpect(status().isOk());
        deleteJson(USERS + "/" + target).andExpect(status().isNoContent());

        for (String body : List.of(listBody, detailBody, updateBody)) {
            assertThat(body).doesNotContain("password").doesNotContain("forcePasswordChange")
                    .doesNotContain("passwordHash").doesNotContain("failedLoginAttempts");
        }
        assertThat(redis.keys("susa:test:auth:session:*")).hasSameSizeAs(before);
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new LoginRequest(email, password))))
                .andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private ResultActions getJson(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + access));
    }

    private ResultActions putJson(String url, Object body) throws Exception {
        return mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(body)));
    }

    private ResultActions patchJson(String url, Object body) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(body)));
    }

    private ResultActions deleteJson(String url) throws Exception {
        return mvc.perform(delete(url).header("Authorization", "Bearer " + access));
    }

    private long roleId(String code) {
        return db.queryForObject("SELECT id FROM app_roles WHERE role_code=?", Long.class, code);
    }

    private long insertUser(String employeeId, String firstName, String lastName, String email,
            String status, Long departmentId, String createdAt) {
        return db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,department_id,account_status,created_at)
                VALUES (?,?,?,?,?,?,?) RETURNING id
                """, Long.class, employeeId, firstName, lastName, email, departmentId, status,
                Timestamp.valueOf(createdAt));
    }

    private void addCredentials(long userId, String password, boolean forceChange) {
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,?)",
                userId, encoder.encode(password), forceChange);
    }

    private void assign(long userId, String roleCode) {
        db.update("INSERT INTO app_user_roles(user_id,role_id,assigned_at) SELECT ?,id,CURRENT_TIMESTAMP FROM app_roles WHERE role_code=?",
                userId, roleCode);
    }

    private void assign(long userId, String roleCode, long assignedBy, String assignedAt) {
        db.update("INSERT INTO app_user_roles(user_id,role_id,assigned_by,assigned_at) SELECT CAST(? AS BIGINT),id,CAST(? AS BIGINT),CAST(? AS TIMESTAMP) FROM app_roles WHERE role_code=?",
                userId, assignedBy, assignedAt, roleCode);
    }

    private Map<String, Object> updateRequest(long roleId, String employeeId, String email) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("employeeId", employeeId);
        request.put("phoneNumber", null);
        request.put("firstName", "Updated");
        request.put("lastName", "User");
        request.put("email", email);
        request.put("departmentId", null);
        request.put("accountStatus", "ACTIVE");
        request.put("roleId", roleId);
        return request;
    }
}
