package com.smartAiUniversityAssistant.seniorproject.feature.authentication;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec file 62: a proven self-registration waits for a Super Admin, who approves it (the person gets the approved
 * mail and can sign in) or rejects it (the account is deleted and the person gets the not-approved mail). Real
 * database, Redis and security chain; only the outgoing mail and Google's token check are replaced. Every account
 * has an email that starts with "sr-" and is removed again, as in {@link AdminRegistrationIntegrationTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@TestPropertySource(properties = {
        "app.admin-registration.google.client-id=test-client.apps.googleusercontent.com",
        "app.admin-registration.verification-url=https://admin.example.test/verify-email"})
class AdminRegistrationApprovalIntegrationTests extends IntegrationSupport {
    private static final String AUTH = "/api/v1/admin/auth";
    private static final String USERS = "/api/v1/admin/users";
    private static final String PASSWORD = "a long passphrase for rsu";
    private static final String OTHER_PASSWORD = "another long passphrase 2";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean AdminMailService mail;
    @MockitoBean GoogleIdTokenVerifier google;
    private long superAdmin;
    private String access;

    @BeforeEach
    void fixture() throws Exception {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) c -> { c.serverCommands().flushDb(); return null; });
        cleanUp();
        db.update("UPDATE app_roles SET is_active=TRUE");
        superAdmin = account("sr-super@rsu.ac.th", "ACTIVE", "SUPER_ADMIN", PASSWORD);
        access = token("sr-super@rsu.ac.th", PASSWORD);
    }

    @AfterEach
    void cleanUp() {
        String mine = "(SELECT id FROM app_users WHERE lower(email) LIKE 'sr-%')";
        db.update("DELETE FROM app_user_email_verifications WHERE user_id IN " + mine);
        db.update("DELETE FROM app_user_external_identities WHERE user_id IN " + mine);
        db.update("DELETE FROM app_user_roles WHERE user_id IN " + mine);
        db.update("DELETE FROM appuser_credentials WHERE user_id IN " + mine);
        db.update("UPDATE app_users SET updated_by=NULL WHERE updated_by IN " + mine);
        db.update("DELETE FROM app_users WHERE lower(email) LIKE 'sr-%'");
    }

    // ---------------------------------------------------------------- approve

    @Test
    void aSuperAdminApprovesAWaitingFormRegistrationAndThePersonCanSignIn() throws Exception {
        long id = waitingFormRegistration("sr-wait@rsu.ac.th");
        long other = waitingFormRegistration("sr-other@rsu.ac.th");
        long unverified = formRegistration("sr-unverified@rsu.ac.th");
        long active = account("sr-active@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        clearInvocations(mail);

        // The Super Admin finds the waiting registrations, and only those, with the status filter.
        mvc.perform(get(USERS).param("accountStatus", "PENDING_APPROVAL").param("sort", "id,asc").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(2))
                .andExpect(jsonPath("content[0].id").value(id)).andExpect(jsonPath("content[1].id").value(other))
                .andExpect(jsonPath("content[0].accountStatus").value("PENDING_APPROVAL"));
        mvc.perform(get(USERS + "/" + id).header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("accountStatus").value("PENDING_APPROVAL")).andExpect(jsonPath("employeeId").doesNotExist());
        login("sr-wait@rsu.ac.th", PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ACCOUNT_PENDING_APPROVAL"));

        clock.advance(Duration.ofMinutes(5));
        mvc.perform(post(USERS + "/" + id + "/approve").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("id").value(id)).andExpect(jsonPath("email").value("sr-wait@rsu.ac.th"))
                .andExpect(jsonPath("accountStatus").value("ACTIVE")).andExpect(jsonPath("roles[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("updatedBy").value(superAdmin));
        var row = db.queryForMap("SELECT account_status, updated_by, updated_at, email_verified_at FROM app_users WHERE id=?", id);
        assertThat(row).containsEntry("account_status", "ACTIVE").doesNotContainEntry("updated_at", null)
                .doesNotContainEntry("email_verified_at", null);
        assertThat(((Number) row.get("updated_by")).longValue()).isEqualTo(superAdmin);
        verify(mail).sendApproved("sr-wait@rsu.ac.th", "Somchai");
        verifyNoMoreInteractions(mail);

        // The person signs in with the password from the form; the other waiting registration still waits.
        login("sr-wait@rsu.ac.th", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("user.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("user.roles[0].code").value("ADMIN"));
        login("sr-other@rsu.ac.th", PASSWORD).andExpect(status().isForbidden());
        mvc.perform(get(USERS).param("accountStatus", "PENDING_APPROVAL").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(1)).andExpect(jsonPath("content[0].id").value(other));

        // Only a waiting registration can be approved: not twice, not before the email is proven, not an active account.
        for (long notWaiting : List.of(id, unverified, active, superAdmin))
            mvc.perform(post(USERS + "/" + notWaiting + "/approve").header("Authorization", bearer()))
                    .andExpect(status().isConflict()).andExpect(jsonPath("code").value("REGISTRATION_NOT_PENDING"))
                    .andExpect(jsonPath("message").value("This account is not waiting for approval."));
        assertThat(accountStatus(unverified)).isEqualTo("PENDING_VERIFICATION");
        verify(mail, times(1)).sendApproved(any(), any());
    }

    @Test
    void anApprovedGoogleRegistrationSignsInWithGoogle() throws Exception {
        when(google.verify("anan")).thenReturn(new GoogleIdentity("g-anan", "sr-anan@rsu.ac.th", true, "rsu.ac.th",
                "Anan", "Suksan", "Anan Suksan"));
        postJson(AUTH + "/google", Map.of("idToken", "anan")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCOUNT_PENDING_APPROVAL"));
        long id = db.queryForObject("SELECT id FROM app_users WHERE email='sr-anan@rsu.ac.th'", Long.class);
        verify(mail).sendAwaitingApproval("sr-anan@rsu.ac.th", "Anan");

        mvc.perform(post(USERS + "/" + id + "/approve").header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("accountStatus").value("ACTIVE"));
        verify(mail).sendApproved("sr-anan@rsu.ac.th", "Anan");
        postJson(AUTH + "/google", Map.of("idToken", "anan")).andExpect(status().isOk())
                .andExpect(jsonPath("accessToken").isString()).andExpect(jsonPath("user.id").value(id));
        // Nobody knows the password of a Google registration.
        login("sr-anan@rsu.ac.th", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongPasswordForAWaitingRegistrationCountsTowardsTheLockOut() throws Exception {
        long id = waitingFormRegistration("sr-guess@rsu.ac.th");
        for (int attempt = 0; attempt < 3; attempt++)
            login("sr-guess@rsu.ac.th", OTHER_PASSWORD).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        assertThat(db.queryForObject("SELECT failed_login_attempts FROM appuser_credentials WHERE user_id=?", Integer.class, id))
                .isEqualTo(3);
        // The right password is the only way to learn that the registration waits, and it resets the count.
        login("sr-guess@rsu.ac.th", PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("code").value("ACCOUNT_PENDING_APPROVAL"));
        assertThat(db.queryForObject("SELECT failed_login_attempts FROM appuser_credentials WHERE user_id=?", Integer.class, id))
                .isZero();
    }

    // ---------------------------------------------------------------- reject

    @Test
    void rejectingAWaitingRegistrationDeletesItAndMailsThePerson() throws Exception {
        long id = waitingFormRegistration("sr-student@rsu.ac.th");

        clock.advance(Duration.ofMinutes(5));
        mvc.perform(post(USERS + "/" + id + "/reject").header("Authorization", bearer()))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(""));
        var row = db.queryForMap("SELECT is_deleted, account_status, email, updated_by FROM app_users WHERE id=?", id);
        // Deleted like DELETE /users/{id}: the row and its email stay, so the address stays taken.
        assertThat(row).containsEntry("is_deleted", true).containsEntry("account_status", "PENDING_APPROVAL")
                .containsEntry("email", "sr-student@rsu.ac.th");
        assertThat(((Number) row.get("updated_by")).longValue()).isEqualTo(superAdmin);
        verify(mail).sendRejected("sr-student@rsu.ac.th", "Somchai");
        verifyNoMoreInteractions(mail);

        login("sr-student@rsu.ac.th", PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        mvc.perform(get(USERS + "/" + id).header("Authorization", bearer())).andExpect(status().isNotFound());
        mvc.perform(get(USERS).param("accountStatus", "PENDING_APPROVAL").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(0));
        // The same address cannot register again, and nothing tells the caller so.
        clearInvocations(mail);
        postJson(AUTH + "/register", form("sr-student@rsu.ac.th", OTHER_PASSWORD)).andExpect(status().isAccepted());
        verifyNoInteractions(mail);
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE lower(email)='sr-student@rsu.ac.th'", Integer.class)).isOne();
        // A deleted account is unknown to approve and reject.
        for (String decision : List.of("approve", "reject"))
            mvc.perform(post(USERS + "/" + id + "/" + decision).header("Authorization", bearer()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("USER_NOT_FOUND"));
    }

    @Test
    void aRejectedGoogleRegistrationCannotSignInWithGoogleAgain() throws Exception {
        when(google.verify("student")).thenReturn(new GoogleIdentity("g-student", "sr-g-student@rsu.ac.th", true, "rsu.ac.th",
                "Mali", "Dee", "Mali Dee"));
        postJson(AUTH + "/google", Map.of("idToken", "student")).andExpect(status().isForbidden());
        long id = db.queryForObject("SELECT id FROM app_users WHERE email='sr-g-student@rsu.ac.th'", Long.class);

        mvc.perform(post(USERS + "/" + id + "/reject").header("Authorization", bearer())).andExpect(status().isNoContent());
        verify(mail).sendRejected("sr-g-student@rsu.ac.th", "Mali");
        postJson(AUTH + "/google", Map.of("idToken", "student")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCOUNT_NOT_ACTIVE"));
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE lower(email)='sr-g-student@rsu.ac.th'", Integer.class)).isOne();
    }

    @Test
    void rejectingAnUnverifiedRegistrationSendsNoMailAndEndsItsLink() throws Exception {
        long id = formRegistration("sr-nobody@rsu.ac.th");
        String link = sentToken("sr-nobody@rsu.ac.th");
        clearInvocations(mail);

        mvc.perform(post(USERS + "/" + id + "/reject").header("Authorization", bearer())).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT is_deleted FROM app_users WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_email_verifications WHERE user_id=?", Integer.class, id)).isZero();
        // Nobody proved the address, so nobody is told.
        verifyNoInteractions(mail);
        postJson(AUTH + "/verify-email", Map.of("token", link)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_VERIFICATION_TOKEN"));
        postJson(AUTH + "/resend-verification", Map.of("email", "sr-nobody@rsu.ac.th")).andExpect(status().isAccepted());
        verifyNoInteractions(mail);
    }

    @Test
    void onlyAWaitingRegistrationCanBeRejected() throws Exception {
        long active = account("sr-active@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        long suspended = account("sr-suspended@rsu.ac.th", "SUSPENDED", "ADMIN", PASSWORD);
        for (long target : List.of(active, suspended, superAdmin))
            mvc.perform(post(USERS + "/" + target + "/reject").header("Authorization", bearer()))
                    .andExpect(status().isConflict()).andExpect(jsonPath("code").value("REGISTRATION_NOT_PENDING"));
        mvc.perform(post(USERS + "/999999999/reject").header("Authorization", bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("USER_NOT_FOUND"));
        mvc.perform(post(USERS + "/999999999/approve").header("Authorization", bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("USER_NOT_FOUND"));
        for (String decision : List.of("approve", "reject")) {
            mvc.perform(post(USERS + "/0/" + decision).header("Authorization", bearer())).andExpect(status().isBadRequest());
            mvc.perform(post(USERS + "/abc/" + decision).header("Authorization", bearer())).andExpect(status().isBadRequest());
        }
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE lower(email) LIKE 'sr-%' AND is_deleted", Integer.class)).isZero();
        verifyNoInteractions(mail);
    }

    // ---------------------------------------------------------------- who may decide

    @Test
    void onlyASuperAdminDecides() throws Exception {
        long id = waitingFormRegistration("sr-wait@rsu.ac.th");
        account("sr-admin@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        account("sr-academic@rsu.ac.th", "ACTIVE", "ACADEMIC_ADMIN", PASSWORD);
        clearInvocations(mail);

        for (String email : List.of("sr-admin@rsu.ac.th", "sr-academic@rsu.ac.th")) {
            String other = "Bearer " + token(email, PASSWORD);
            for (String decision : List.of("approve", "reject"))
                mvc.perform(post(USERS + "/" + id + "/" + decision).header("Authorization", other)).andExpect(status().isForbidden());
            mvc.perform(get(USERS).param("accountStatus", "PENDING_APPROVAL").header("Authorization", other))
                    .andExpect(status().isForbidden());
        }
        for (String decision : List.of("approve", "reject"))
            mvc.perform(post(USERS + "/" + id + "/" + decision)).andExpect(status().isUnauthorized());
        // A Super Admin who still has to change the temporary password decides nothing.
        db.update("UPDATE appuser_credentials SET force_password_change=TRUE WHERE user_id=?", superAdmin);
        String temporary = "Bearer " + token("sr-super@rsu.ac.th", PASSWORD);
        mvc.perform(post(USERS + "/" + id + "/approve").header("Authorization", temporary)).andExpect(status().isForbidden());

        assertThat(accountStatus(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(db.queryForObject("SELECT is_deleted FROM app_users WHERE id=?", Boolean.class, id)).isFalse();
        verifyNoInteractions(mail);
    }

    // ---------------------------------------------------------------- the other user endpoints

    @Test
    void aWaitingRegistrationCanBeCompletedButLeavesItsStatusOnlyByTheDecision() throws Exception {
        long id = waitingFormRegistration("sr-wait@rsu.ac.th");
        long unverified = formRegistration("sr-unverified@rsu.ac.th");
        long active = account("sr-active@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        long academic = db.queryForObject("SELECT id FROM app_roles WHERE role_code='ACADEMIC_ADMIN'", Long.class);
        clearInvocations(mail);

        // Before approving, the Super Admin enters the employee ID and picks the role; the status stays as it is.
        var update = profile("sr-wait@rsu.ac.th", "SR-EMP-1", "PENDING_APPROVAL", academic);
        putJson(USERS + "/" + id, update).andExpect(status().isOk()).andExpect(jsonPath("employeeId").value("SR-EMP-1"))
                .andExpect(jsonPath("accountStatus").value("PENDING_APPROVAL")).andExpect(jsonPath("roles[0].roleCode").value("ACADEMIC_ADMIN"));

        // Every other status is refused: the decision is approve or reject.
        for (String other : List.of("ACTIVE", "INACTIVE", "SUSPENDED")) {
            putJson(USERS + "/" + id, profile("sr-wait@rsu.ac.th", "SR-EMP-1", other, academic)).andExpect(status().isConflict())
                    .andExpect(jsonPath("code").value("REGISTRATION_DECISION_REQUIRED"))
                    .andExpect(jsonPath("message").value("Approve or reject this registration first."));
            patchJson(USERS + "/" + id + "/status", Map.of("accountStatus", other)).andExpect(status().isConflict())
                    .andExpect(jsonPath("code").value("REGISTRATION_DECISION_REQUIRED"));
            patchJson(USERS + "/" + unverified + "/status", Map.of("accountStatus", other)).andExpect(status().isConflict())
                    .andExpect(jsonPath("code").value("REGISTRATION_DECISION_REQUIRED"));
        }
        // A pending status is never set by hand: not the other one, not on an active account, not with PATCH.
        putJson(USERS + "/" + id, profile("sr-wait@rsu.ac.th", "SR-EMP-1", "PENDING_VERIFICATION", academic))
                .andExpect(status().isBadRequest());
        putJson(USERS + "/" + active, profile("sr-active@rsu.ac.th", "SR-" + active, "PENDING_APPROVAL", academic))
                .andExpect(status().isBadRequest());
        patchJson(USERS + "/" + id + "/status", Map.of("accountStatus", "PENDING_APPROVAL")).andExpect(status().isBadRequest());
        assertThat(accountStatus(id)).isEqualTo("PENDING_APPROVAL");
        assertThat(accountStatus(unverified)).isEqualTo("PENDING_VERIFICATION");
        assertThat(accountStatus(active)).isEqualTo("ACTIVE");

        // The approval keeps what the Super Admin entered.
        mvc.perform(post(USERS + "/" + id + "/approve").header("Authorization", bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("employeeId").value("SR-EMP-1")).andExpect(jsonPath("roles[0].roleCode").value("ACADEMIC_ADMIN"));
        login("sr-wait@rsu.ac.th", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("user.roles[0].code").value("ACADEMIC_ADMIN"));
        // Once approved, it is a normal account again.
        patchJson(USERS + "/" + id + "/status", Map.of("accountStatus", "SUSPENDED")).andExpect(status().isOk());
        verify(mail).sendApproved("sr-wait@rsu.ac.th", "Somchai");
        verifyNoMoreInteractions(mail);
    }

    @Test
    void deleteStillRemovesAWaitingRegistrationWithoutAMail() throws Exception {
        long id = waitingFormRegistration("sr-wait@rsu.ac.th");
        clearInvocations(mail);
        mvc.perform(delete(USERS + "/" + id).header("Authorization", bearer())).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT is_deleted FROM app_users WHERE id=?", Boolean.class, id)).isTrue();
        verifyNoInteractions(mail);
    }

    // ---------------------------------------------------------------- helpers

    /** A form registration whose email link was opened: it waits for approval. */
    private long waitingFormRegistration(String email) throws Exception {
        long id = formRegistration(email);
        postJson(AUTH + "/verify-email", Map.of("token", sentToken(email))).andExpect(status().isOk())
                .andExpect(jsonPath("accountStatus").value("PENDING_APPROVAL"));
        verify(mail).sendAwaitingApproval(email, "Somchai");
        clearInvocations(mail);
        assertThat(accountStatus(id)).isEqualTo("PENDING_APPROVAL");
        return id;
    }

    /** A form registration whose email link was not opened yet. */
    private long formRegistration(String email) throws Exception {
        postJson(AUTH + "/register", form(email, PASSWORD)).andExpect(status().isAccepted());
        return db.queryForObject("SELECT id FROM app_users WHERE email=?", Long.class, email);
    }

    private Map<String, Object> form(String email, String password) {
        var body = new LinkedHashMap<String, Object>();
        body.put("firstName", "Somchai"); body.put("lastName", "Jaidee"); body.put("email", email);
        body.put("password", password); body.put("confirmPassword", password);
        return body;
    }

    private Map<String, Object> profile(String email, String employeeId, String status, long roleId) {
        var body = new LinkedHashMap<String, Object>();
        body.put("employeeId", employeeId); body.put("phoneNumber", null); body.put("firstName", "Somchai");
        body.put("lastName", "Jaidee"); body.put("email", email); body.put("departmentId", null);
        body.put("accountStatus", status); body.put("roleId", roleId);
        return body;
    }

    /** The token of the verification link in the last verification mail to this address. */
    private String sentToken(String email) {
        var link = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).sendVerification(eq(email), any(), link.capture(), any());
        String value = link.getValue();
        return value.substring(value.indexOf("token=") + 6);
    }

    /** An account as a Super Admin creates it: employee ID, password, not self-registered. */
    private long account(String email, String status, String role, String password) {
        long id = db.queryForObject("INSERT INTO app_users(first_name,last_name,email,account_status) VALUES ('Test','User',?,?) RETURNING id",
                Long.class, email, status);
        db.update("UPDATE app_users SET employee_id=? WHERE id=?", "SR-" + id, id);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, encoder.encode(password));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code=?", id, role);
        return id;
    }

    private String accountStatus(long userId) {
        return db.queryForObject("SELECT account_status FROM app_users WHERE id=?", String.class, userId);
    }

    private String token(String email, String password) throws Exception {
        var response = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + access;
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson(AUTH + "/login", Map.of("email", email, "password", password));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType("application/json").content(json.writeValueAsBytes(body)));
    }

    private ResultActions putJson(String path, Object body) throws Exception {
        return mvc.perform(put(path).contentType("application/json").header("Authorization", bearer())
                .content(json.writeValueAsBytes(body)));
    }

    private ResultActions patchJson(String path, Object body) throws Exception {
        return mvc.perform(patch(path).contentType("application/json").header("Authorization", bearer())
                .content(json.writeValueAsBytes(body)));
    }
}
