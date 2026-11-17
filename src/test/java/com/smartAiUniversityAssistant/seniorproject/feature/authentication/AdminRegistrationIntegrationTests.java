package com.smartAiUniversityAssistant.seniorproject.feature.authentication;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
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
 * Spec files 60, 61 and 62: Admin self-registration with the form and with Google, over the real database, Redis and
 * security chain. Since spec file 62 a proven registration waits for a Super Admin; this class approves it in the
 * database, and {@code AdminRegistrationApprovalIntegrationTests} covers the approve and reject endpoints. Only the outgoing mail and Google's token check are replaced. Every account of this class has an
 * email that starts with "sr-", and the class removes them again, so other classes can still delete all users.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@TestPropertySource(properties = {
        "app.admin-registration.google.client-id=test-client.apps.googleusercontent.com",
        "app.admin-registration.verification-url=https://admin.example.test/verify-email"})
class AdminRegistrationIntegrationTests extends IntegrationSupport {
    private static final String AUTH = "/api/v1/admin/auth";
    private static final String PASSWORD = "a long passphrase for rsu";
    private static final String OTHER_PASSWORD = "another long passphrase 2";
    private static final String ACCEPTED = "If this address can be registered, we have sent an email to it. "
            + "Open the link in that email to finish the registration.";
    private static final String WAITING = "Your registration is waiting for approval by an administrator.";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired StringRedisTemplate redis;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @MockitoBean AdminMailService mail;
    @MockitoBean GoogleIdTokenVerifier google;

    @BeforeEach
    void fixture() {
        clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        redis.execute((org.springframework.data.redis.core.RedisCallback<Void>) c -> { c.serverCommands().flushDb(); return null; });
        cleanUp();
        db.update("UPDATE app_roles SET is_active=TRUE");
    }

    @AfterEach
    void cleanUp() {
        String mine = "(SELECT id FROM app_users WHERE lower(email) LIKE 'sr-%')";
        db.update("DELETE FROM app_user_email_verifications WHERE user_id IN " + mine);
        db.update("DELETE FROM app_user_external_identities WHERE user_id IN " + mine);
        db.update("DELETE FROM app_user_roles WHERE user_id IN " + mine);
        db.update("DELETE FROM appuser_credentials WHERE user_id IN " + mine);
        db.update("DELETE FROM app_users WHERE lower(email) LIKE 'sr-%'");
    }

    // ---------------------------------------------------------------- registration form

    @Test
    void formRegistrationVerifiesTheEmailWaitsForApprovalAndThenSignsIn() throws Exception {
        // A stale or foreign Bearer token on a public route is ignored, as for login.
        mvc.perform(post(AUTH + "/register").contentType("application/json").header("Authorization", "Bearer not-a-token")
                        .content(json.writeValueAsBytes(form(" SR-Somchai@RSU.AC.TH ", PASSWORD))))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("email").value("sr-somchai@rsu.ac.th")).andExpect(jsonPath("message").value(ACCEPTED))
                .andExpect(jsonPath("id").doesNotExist());

        var user = db.queryForMap("SELECT * FROM app_users WHERE email='sr-somchai@rsu.ac.th'");
        long id = ((Number) user.get("id")).longValue();
        assertThat(user).containsEntry("account_status", "PENDING_VERIFICATION").containsEntry("registration_source", "SELF_FORM")
                .containsEntry("first_name", "Somchai").containsEntry("last_name", "Jaidee").containsEntry("phone_number", "02 791 6000")
                .containsEntry("employee_id", null).containsEntry("email_verified_at", null).containsEntry("created_by", null)
                .containsEntry("is_deleted", false);
        assertThat(roles(id)).containsExactly("ADMIN");
        var credential = db.queryForMap("SELECT * FROM appuser_credentials WHERE user_id=?", id);
        assertThat(credential).containsEntry("force_password_change", false);
        assertThat(encoder.matches(PASSWORD, (String) credential.get("password_hash"))).isTrue();

        String token = sentToken("sr-somchai@rsu.ac.th", "Somchai");
        assertThat(TokenSupport.canonical(token, 32)).isTrue();
        // Only the hash of the token is stored.
        assertThat(db.queryForObject("SELECT token_hash FROM app_user_email_verifications WHERE user_id=?", String.class, id))
                .isEqualTo(TokenSupport.digest(token)).doesNotContain(token);

        // Not verified: no login, and the reason is not revealed.
        login("sr-somchai@rsu.ac.th", PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));

        postJson("/verify-email", Map.of("token", token)).andExpect(status().isOk())
                .andExpect(jsonPath("email").value("sr-somchai@rsu.ac.th")).andExpect(jsonPath("accountStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("message").value("Your email is verified. " + WAITING));
        verify(mail).sendAwaitingApproval("sr-somchai@rsu.ac.th", "Somchai");
        assertThat(db.queryForMap("SELECT account_status, email_verified_at FROM app_users WHERE id=?", id))
                .containsEntry("account_status", "PENDING_APPROVAL").doesNotContainEntry("email_verified_at", null);
        assertThat(count("app_user_email_verifications", id)).isZero();
        // A link works once.
        postJson("/verify-email", Map.of("token", token)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_VERIFICATION_TOKEN"));
        verify(mail, times(1)).sendAwaitingApproval(any(), any());

        // Waiting for approval: the right password is told why, a wrong one learns nothing.
        login("sr-somchai@rsu.ac.th", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCOUNT_PENDING_APPROVAL")).andExpect(jsonPath("message").value(WAITING))
                .andExpect(jsonPath("accessToken").doesNotExist());
        login("sr-somchai@rsu.ac.th", OTHER_PASSWORD).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        // Registering again while waiting changes nothing and repeats why nothing happens.
        clearInvocations(mail);
        postJson("/register", form("sr-somchai@rsu.ac.th", OTHER_PASSWORD)).andExpect(status().isAccepted())
                .andExpect(jsonPath("message").value(ACCEPTED));
        postJson("/resend-verification", Map.of("email", "sr-somchai@rsu.ac.th")).andExpect(status().isAccepted());
        verify(mail).sendAwaitingApproval("sr-somchai@rsu.ac.th", "Somchai");
        verifyNoMoreInteractions(mail);
        assertThat(count("app_user_email_verifications", id)).isZero();

        approve(id);

        // The email is stored in lower case; the person may type it as on the form.
        var session = body(login("SR-Somchai@rsu.ac.th", PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("forcePasswordChange").value(false)).andExpect(jsonPath("user.roles[0].code").value("ADMIN"))
                .andExpect(jsonPath("user.employeeId").doesNotExist()));
        String access = session.get("accessToken").asText();
        mvc.perform(get(AUTH + "/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                .andExpect(jsonPath("email").value("sr-somchai@rsu.ac.th"));
        // The default role is a normal Admin: academic screens yes, user management no.
        mvc.perform(get("/api/v1/admin/faculties").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + access)).andExpect(status().isForbidden());
    }

    @Test
    void registrationRejectsOtherDomainsWeakPasswordsAndBadInput() throws Exception {
        for (String email : List.of("sr-a@gmail.com", "sr-a@mail.rsu.ac.th", "sr-a@rsu.ac.th.evil.test", "sr-a@rsu.ac.thx"))
            postJson("/register", form(email, PASSWORD)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("code").value("EMAIL_DOMAIN_NOT_ALLOWED"))
                    .andExpect(jsonPath("message").value("Only @rsu.ac.th email addresses can register."));
        postJson("/register", form("sr-a@rsu.ac.th", "too short")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("PASSWORD_POLICY_VIOLATION"));
        var mismatch = form("sr-a@rsu.ac.th", PASSWORD);
        mismatch.put("confirmPassword", OTHER_PASSWORD);
        postJson("/register", mismatch).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PASSWORD_CONFIRMATION_MISMATCH"));
        for (String missing : List.of("firstName", "lastName", "email", "password", "confirmPassword")) {
            var body = form("sr-a@rsu.ac.th", PASSWORD);
            body.remove(missing);
            postJson("/register", body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        // A caller cannot choose a role, a status or an employee ID.
        for (String extra : List.of("roleId", "accountStatus", "employeeId")) {
            var body = form("sr-a@rsu.ac.th", PASSWORD);
            body.put(extra, "1");
            postJson("/register", body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        }
        var phone = form("sr-a@rsu.ac.th", PASSWORD);
        phone.put("phoneNumber", "call me");
        postJson("/register", phone).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        postJson("/verify-email", Map.of("token", "not-a-token")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_VERIFICATION_TOKEN"));
        postJson("/verify-email", Map.of("token", TokenSupport.random(32))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_VERIFICATION_TOKEN"));

        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE lower(email) LIKE 'sr-%'", Integer.class)).isZero();
        verifyNoInteractions(mail);
    }

    @Test
    void registeringAnAddressThatHasAnAccountAnswersTheSameAndChangesNothing() throws Exception {
        long active = account("SR-Active@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        long suspended = account("sr-suspended@rsu.ac.th", "SUSPENDED", "ADMIN", PASSWORD);

        postJson("/register", form("sr-active@rsu.ac.th", OTHER_PASSWORD)).andExpect(status().isAccepted())
                .andExpect(jsonPath("email").value("sr-active@rsu.ac.th")).andExpect(jsonPath("message").value(ACCEPTED));
        postJson("/register", form("sr-suspended@rsu.ac.th", OTHER_PASSWORD)).andExpect(status().isAccepted())
                .andExpect(jsonPath("message").value(ACCEPTED));

        // The owner of the active account is told; a suspended account gets no mail.
        verify(mail).sendAlreadyRegistered("SR-Active@rsu.ac.th", "Test");
        verify(mail, never()).sendVerification(any(), any(), any(), any());
        verifyNoMoreInteractions(mail);
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE lower(email) LIKE 'sr-%'", Integer.class)).isEqualTo(2);
        assertThat(count("app_user_email_verifications", active) + count("app_user_email_verifications", suspended)).isZero();
        // The existing password still works and the new one does not.
        login("SR-Active@rsu.ac.th", PASSWORD).andExpect(status().isOk());
        login("SR-Active@rsu.ac.th", OTHER_PASSWORD).andExpect(status().isUnauthorized());
        // No verification mail can be requested for it either.
        postJson("/resend-verification", Map.of("email", "sr-active@rsu.ac.th")).andExpect(status().isAccepted())
                .andExpect(jsonPath("message").value(ACCEPTED));
        postJson("/resend-verification", Map.of("email", "sr-nobody@rsu.ac.th")).andExpect(status().isAccepted());
        postJson("/resend-verification", Map.of("email", "sr-nobody@gmail.com")).andExpect(status().isAccepted());
        verify(mail, never()).sendVerification(any(), any(), any(), any());
    }

    @Test
    void aNewerRegistrationOfAnUnverifiedAddressReplacesTheOlderOne() throws Exception {
        postJson("/register", form("sr-twice@rsu.ac.th", PASSWORD)).andExpect(status().isAccepted());
        String first = sentToken("sr-twice@rsu.ac.th", "Somchai");
        clearInvocations(mail);
        var second = form("sr-twice@rsu.ac.th", OTHER_PASSWORD);
        second.put("firstName", "Somsri");
        postJson("/register", second).andExpect(status().isAccepted()).andExpect(jsonPath("message").value(ACCEPTED));
        String latest = sentToken("sr-twice@rsu.ac.th", "Somsri");

        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE email='sr-twice@rsu.ac.th'", Integer.class)).isOne();
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_email_verifications", Integer.class)).isOne();
        postJson("/verify-email", Map.of("token", first)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_VERIFICATION_TOKEN"));
        postJson("/verify-email", Map.of("token", latest)).andExpect(status().isOk());
        approve(db.queryForObject("SELECT id FROM app_users WHERE email='sr-twice@rsu.ac.th'", Long.class));
        login("sr-twice@rsu.ac.th", PASSWORD).andExpect(status().isUnauthorized());
        login("sr-twice@rsu.ac.th", OTHER_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("user.firstName").value("Somsri"));
    }

    @Test
    void aNewLinkReplacesTheOldOneAndALinkExpires() throws Exception {
        postJson("/register", form("sr-late@rsu.ac.th", PASSWORD)).andExpect(status().isAccepted());
        String first = sentToken("sr-late@rsu.ac.th", "Somchai");
        clearInvocations(mail);
        postJson("/resend-verification", Map.of("email", " SR-Late@rsu.ac.th ")).andExpect(status().isAccepted())
                .andExpect(jsonPath("email").value("sr-late@rsu.ac.th")).andExpect(jsonPath("message").value(ACCEPTED));
        String second = sentToken("sr-late@rsu.ac.th", "Somchai");
        assertThat(second).isNotEqualTo(first);
        postJson("/verify-email", Map.of("token", first)).andExpect(status().isBadRequest());

        // The link is valid for 24 hours.
        clock.advance(Duration.ofHours(24).plusSeconds(1));
        postJson("/verify-email", Map.of("token", second)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_VERIFICATION_TOKEN"));
        assertThat(db.queryForObject("SELECT account_status FROM app_users WHERE email='sr-late@rsu.ac.th'", String.class))
                .isEqualTo("PENDING_VERIFICATION");

        clearInvocations(mail);
        postJson("/resend-verification", Map.of("email", "sr-late@rsu.ac.th")).andExpect(status().isAccepted());
        postJson("/verify-email", Map.of("token", sentToken("sr-late@rsu.ac.th", "Somchai"))).andExpect(status().isOk());

        // One address receives at most three registration mails a minute (one register and two resends above).
        postJson("/resend-verification", Map.of("email", "sr-late@rsu.ac.th")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("code").value("TOO_MANY_REQUESTS"));
        postJson("/register", form("sr-late@rsu.ac.th", PASSWORD)).andExpect(status().isTooManyRequests());
    }

    // ---------------------------------------------------------------- Google sign-in

    @Test
    void theFirstGoogleSignInCreatesAWaitingAccountAndLaterOnesReuseIt() throws Exception {
        when(google.verify("google-id-token")).thenReturn(identity("g-100", "SR-Anan@rsu.ac.th", true, "rsu.ac.th", "Anan", "Suksan"));

        // The account is saved, but it waits for a Super Admin: no tokens yet.
        postJson("/google", Map.of("idToken", "google-id-token")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCOUNT_PENDING_APPROVAL")).andExpect(jsonPath("message").value(WAITING))
                .andExpect(jsonPath("accessToken").doesNotExist());
        long id = db.queryForObject("SELECT id FROM app_users WHERE email='sr-anan@rsu.ac.th'", Long.class);
        assertThat(db.queryForMap("SELECT * FROM app_users WHERE id=?", id)).containsEntry("registration_source", "GOOGLE")
                .containsEntry("account_status", "PENDING_APPROVAL").containsEntry("employee_id", null)
                .containsEntry("first_name", "Anan").containsEntry("last_name", "Suksan").doesNotContainEntry("email_verified_at", null);
        assertThat(db.queryForMap("SELECT provider, provider_subject FROM app_user_external_identities WHERE user_id=?", id))
                .containsEntry("provider", "GOOGLE").containsEntry("provider_subject", "g-100");
        assertThat(roles(id)).containsExactly("ADMIN");
        assertThat(count("app_user_email_verifications", id)).isZero();
        verify(mail).sendAwaitingApproval("sr-anan@rsu.ac.th", "Anan");
        // Trying again while waiting: the same answer, no second account and no second mail.
        postJson("/google", Map.of("idToken", "google-id-token")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCOUNT_PENDING_APPROVAL"));
        verify(mail, times(1)).sendAwaitingApproval(any(), any());

        approve(id);
        var first = body(postJson("/google", Map.of("idToken", "google-id-token")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("tokenType").value("Bearer"))
                .andExpect(jsonPath("forcePasswordChange").value(false)).andExpect(jsonPath("user.email").value("sr-anan@rsu.ac.th"))
                .andExpect(jsonPath("user.firstName").value("Anan")).andExpect(jsonPath("user.lastName").value("Suksan"))
                .andExpect(jsonPath("user.accountStatus").value("ACTIVE")).andExpect(jsonPath("user.roles[0].code").value("ADMIN")));
        assertThat(first.get("user").get("id").asLong()).isEqualTo(id);

        // The session is a normal Admin session: /me, refresh and logout work.
        String access = first.get("accessToken").asText();
        mvc.perform(get(AUTH + "/me").header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                .andExpect(jsonPath("id").value(id));
        postJson("/refresh", Map.of("refreshToken", first.get("refreshToken").asText())).andExpect(status().isOk());

        var again = body(postJson("/google", Map.of("idToken", "google-id-token")).andExpect(status().isOk()));
        assertThat(again.get("user").get("id").asLong()).isEqualTo(id);
        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE lower(email)='sr-anan@rsu.ac.th'", Integer.class)).isOne();
        assertThat(count("app_user_external_identities", id)).isOne();
        verify(mail, times(1)).sendAwaitingApproval(any(), any());
        verifyNoMoreInteractions(mail);

        // The account has no password anybody knows.
        String hash = db.queryForObject("SELECT password_hash FROM appuser_credentials WHERE user_id=?", String.class, id);
        assertThat(hash).startsWith("$2");
        login("sr-anan@rsu.ac.th", PASSWORD).andExpect(status().isUnauthorized());
        // A Super Admin who suspends the account also stops Google sign-in.
        db.update("UPDATE app_users SET account_status='SUSPENDED' WHERE id=?", id);
        postJson("/google", Map.of("idToken", "google-id-token")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCOUNT_NOT_ACTIVE"));
        mvc.perform(get(AUTH + "/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test
    void googleSignInRejectsOtherDomainsUnverifiedEmailsAndInvalidTokens() throws Exception {
        when(google.verify("gmail")).thenReturn(identity("g-1", "sr-a@gmail.com", true, null, "A", "B"));
        when(google.verify("subdomain")).thenReturn(identity("g-2", "sr-a@student.rsu.ac.th", true, "student.rsu.ac.th", "A", "B"));
        when(google.verify("other-workspace")).thenReturn(identity("g-3", "sr-a@rsu.ac.th", true, "other.example", "A", "B"));
        when(google.verify("personal")).thenReturn(identity("g-5", "sr-a@rsu.ac.th", true, null, "A", "B"));
        when(google.verify("unverified")).thenReturn(identity("g-4", "sr-a@rsu.ac.th", false, "rsu.ac.th", "A", "B"));
        when(google.verify("forged")).thenThrow(new AuthenticationFailure(401, "INVALID_GOOGLE_TOKEN", "The Google sign-in could not be verified."));

        for (String token : List.of("gmail", "subdomain"))
            postJson("/google", Map.of("idToken", token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("EMAIL_DOMAIN_NOT_ALLOWED"));
        // The right address is not enough: the account must belong to the university's Google Workspace ("hd").
        for (String token : List.of("other-workspace", "personal"))
            postJson("/google", Map.of("idToken", token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("GOOGLE_ACCOUNT_NOT_IN_DOMAIN")).andExpect(jsonPath("accessToken").doesNotExist());
        postJson("/google", Map.of("idToken", "unverified")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("GOOGLE_EMAIL_NOT_VERIFIED"));
        postJson("/google", Map.of("idToken", "forged")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("code").value("INVALID_GOOGLE_TOKEN")).andExpect(jsonPath("accessToken").doesNotExist());
        postJson("/google", Map.of()).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        postJson("/google", Map.of("idToken", "x".repeat(4097))).andExpect(status().isBadRequest());

        assertThat(db.queryForObject("SELECT count(*) FROM app_users WHERE lower(email) LIKE 'sr-%'", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM app_user_external_identities", Integer.class)).isZero();
        verifyNoInteractions(mail);
    }

    @Test
    void googleSignInLinksAnExistingAccountByItsEmailAndKeepsItsRole() throws Exception {
        long existing = account("sr-staff@rsu.ac.th", "ACTIVE", "ACADEMIC_ADMIN", PASSWORD);
        long suspended = account("sr-gone@rsu.ac.th", "SUSPENDED", "ADMIN", PASSWORD);
        long deleted = account("sr-deleted@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        db.update("UPDATE app_users SET is_deleted=TRUE WHERE id=?", deleted);
        when(google.verify("staff")).thenReturn(identity("g-staff", "sr-staff@rsu.ac.th", true, "rsu.ac.th", "Other", "Name"));
        when(google.verify("staff-second-account")).thenReturn(identity("g-other", "sr-staff@rsu.ac.th", true, "rsu.ac.th", "Other", "Name"));
        when(google.verify("gone")).thenReturn(identity("g-gone", "sr-gone@rsu.ac.th", true, "rsu.ac.th", "A", "B"));
        when(google.verify("deleted")).thenReturn(identity("g-deleted", "sr-deleted@rsu.ac.th", true, "rsu.ac.th", "A", "B"));
        when(google.verify("staff-personal")).thenReturn(identity("g-personal", "sr-staff@rsu.ac.th", true, null, "Other", "Name"));

        // A personal Google account that carries the address of an existing account is never linked to it.
        postJson("/google", Map.of("idToken", "staff-personal")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("GOOGLE_ACCOUNT_NOT_IN_DOMAIN"));
        assertThat(count("app_user_external_identities", existing)).isZero();

        postJson("/google", Map.of("idToken", "staff")).andExpect(status().isOk()).andExpect(jsonPath("user.id").value(existing))
                .andExpect(jsonPath("user.roles[0].code").value("ACADEMIC_ADMIN")).andExpect(jsonPath("user.roles.length()").value(1))
                // The profile a Super Admin entered is not overwritten with Google's.
                .andExpect(jsonPath("user.firstName").value("Test")).andExpect(jsonPath("user.employeeId").value("SR-" + existing));
        assertThat(db.queryForMap("SELECT registration_source, email_verified_at FROM app_users WHERE id=?", existing))
                .containsEntry("registration_source", "ADMIN_CREATED").doesNotContainEntry("email_verified_at", null);
        assertThat(count("app_user_external_identities", existing)).isOne();
        // Both ways to sign in work from now on.
        login("sr-staff@rsu.ac.th", PASSWORD).andExpect(status().isOk());
        postJson("/google", Map.of("idToken", "staff")).andExpect(status().isOk());

        postJson("/google", Map.of("idToken", "staff-second-account")).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("GOOGLE_ACCOUNT_MISMATCH"));
        for (String token : List.of("gone", "deleted"))
            postJson("/google", Map.of("idToken", token)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("ACCOUNT_NOT_ACTIVE"));
        assertThat(count("app_user_external_identities", suspended) + count("app_user_external_identities", deleted)).isZero();
        // Nobody registered here, so no mail.
        verifyNoInteractions(mail);
    }

    @Test
    void googleSignInProvesAnUnverifiedFormRegistrationAndDropsItsPassword() throws Exception {
        // Somebody (maybe not the owner of the address) registered with a password and never verified.
        postJson("/register", form("sr-owner@rsu.ac.th", PASSWORD)).andExpect(status().isAccepted());
        String link = sentToken("sr-owner@rsu.ac.th", "Somchai");
        long id = db.queryForObject("SELECT id FROM app_users WHERE email='sr-owner@rsu.ac.th'", Long.class);
        when(google.verify("owner")).thenReturn(identity("g-owner", "sr-owner@rsu.ac.th", true, "rsu.ac.th", "Real", "Owner"));
        when(google.verify("owner-personal")).thenReturn(identity("g-other", "sr-owner@rsu.ac.th", true, null, "Not", "Owner"));

        // Only the university's own Google account of the address can finish the registration.
        postJson("/google", Map.of("idToken", "owner-personal")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("GOOGLE_ACCOUNT_NOT_IN_DOMAIN"));
        assertThat(db.queryForObject("SELECT account_status FROM app_users WHERE id=?", String.class, id)).isEqualTo("PENDING_VERIFICATION");
        assertThat(count("app_user_email_verifications", id)).isOne();

        postJson("/google", Map.of("idToken", "owner")).andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("ACCOUNT_PENDING_APPROVAL"));

        assertThat(db.queryForMap("SELECT account_status, registration_source, email_verified_at FROM app_users WHERE id=?", id))
                .containsEntry("account_status", "PENDING_APPROVAL").containsEntry("registration_source", "SELF_FORM")
                .doesNotContainEntry("email_verified_at", null);
        assertThat(count("app_user_email_verifications", id)).isZero();
        assertThat(count("app_user_external_identities", id)).isOne();
        verify(mail).sendAwaitingApproval("sr-owner@rsu.ac.th", "Somchai");
        // The password chosen before the address was proven no longer opens the account, and neither does its link.
        login("sr-owner@rsu.ac.th", PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        postJson("/verify-email", Map.of("token", link)).andExpect(status().isBadRequest());

        approve(id);
        postJson("/google", Map.of("idToken", "owner")).andExpect(status().isOk()).andExpect(jsonPath("user.id").value(id))
                .andExpect(jsonPath("user.accountStatus").value("ACTIVE"));
        login("sr-owner@rsu.ac.th", PASSWORD).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- Super Admin view and schema

    @Test
    void aSuperAdminSeesPendingRegistrationsAndCanCompleteASelfRegisteredProfile() throws Exception {
        account("sr-super@rsu.ac.th", "ACTIVE", "SUPER_ADMIN", PASSWORD);
        String access = body(login("sr-super@rsu.ac.th", PASSWORD).andExpect(status().isOk())).get("accessToken").asText();
        postJson("/register", form("sr-pending@rsu.ac.th", PASSWORD)).andExpect(status().isAccepted());
        long pending = db.queryForObject("SELECT id FROM app_users WHERE email='sr-pending@rsu.ac.th'", Long.class);
        long adminRole = db.queryForObject("SELECT id FROM app_roles WHERE role_code='ADMIN'", Long.class);

        mvc.perform(get("/api/v1/admin/users").param("accountStatus", "PENDING_VERIFICATION").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(1))
                .andExpect(jsonPath("content[0].id").value(pending)).andExpect(jsonPath("content[0].employeeId").doesNotExist());
        mvc.perform(get("/api/v1/admin/users/" + pending).header("Authorization", "Bearer " + access)).andExpect(status().isOk())
                .andExpect(jsonPath("accountStatus").value("PENDING_VERIFICATION"));
        // PENDING_VERIFICATION is set by the system only.
        mvc.perform(patch("/api/v1/admin/users/" + pending + "/status").contentType("application/json")
                        .header("Authorization", "Bearer " + access).content("{\"accountStatus\":\"PENDING_VERIFICATION\"}"))
                .andExpect(status().isBadRequest());

        // The Super Admin enters the missing employee ID (the account had none) and keeps its status.
        var update = new LinkedHashMap<String, Object>();
        update.put("employeeId", "SR-EMP-77"); update.put("phoneNumber", null); update.put("firstName", "Somchai");
        update.put("lastName", "Jaidee"); update.put("email", "sr-pending@rsu.ac.th"); update.put("departmentId", null);
        update.put("accountStatus", "PENDING_VERIFICATION"); update.put("roleId", adminRole);
        mvc.perform(put("/api/v1/admin/users/" + pending).contentType("application/json")
                        .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("employeeId").value("SR-EMP-77"))
                .andExpect(jsonPath("accountStatus").value("PENDING_VERIFICATION"));
        assertThat(db.queryForObject("SELECT employee_id FROM app_users WHERE id=?", String.class, pending)).isEqualTo("SR-EMP-77");
        // Since spec file 62 it cannot be made active this way: its owner has not proven the address.
        update.put("accountStatus", "ACTIVE");
        mvc.perform(put("/api/v1/admin/users/" + pending).contentType("application/json")
                        .header("Authorization", "Bearer " + access).content(json.writeValueAsBytes(update)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("REGISTRATION_DECISION_REQUIRED"));
        assertThat(db.queryForObject("SELECT account_status FROM app_users WHERE id=?", String.class, pending)).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void theSchemaKeepsEachFactInOnePlaceAndNeverCascades() {
        // app_users holds only facts about the user; the Google link and the pending verification have their own tables.
        assertThat(columns("app_users")).contains("registration_source", "email_verified_at")
                .doesNotContain("google_subject", "google_id", "verification_token", "token_hash");
        assertThat(columns("app_user_external_identities")).containsExactlyInAnyOrder("id", "user_id", "provider", "provider_subject", "linked_at");
        assertThat(columns("app_user_email_verifications")).containsExactlyInAnyOrder("id", "user_id", "token_hash", "expires_at", "created_at");
        assertThat(db.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_name='app_users' AND column_name='employee_id'", String.class)).isEqualTo("YES");
        assertThat(db.queryForObject("""
                SELECT count(*) FROM pg_constraint WHERE contype='f' AND confdeltype<>'a'
                    AND conrelid IN ('app_user_external_identities'::regclass,'app_user_email_verifications'::regclass)""", Integer.class)).isZero();
        assertThat(db.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conrelid IN ('app_user_external_identities'::regclass,'app_user_email_verifications'::regclass,'app_users'::regclass)""", String.class))
                .contains("uk_app_user_external_identities_provider_subject", "uk_app_user_external_identities_user_provider",
                        "fk_app_user_external_identities_user", "chk_app_user_external_identities_provider",
                        "uk_app_user_email_verifications_user", "uk_app_user_email_verifications_token_hash",
                        "fk_app_user_email_verifications_user", "chk_app_user_email_verifications_expiry",
                        "chk_app_users_registration_source", "uk_app_users_employee_id", "uk_app_users_email");

        long a = account("sr-schema-a@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        long b = account("sr-schema-b@rsu.ac.th", "ACTIVE", "ADMIN", PASSWORD);
        assertThat(db.queryForObject("SELECT registration_source FROM app_users WHERE id=?", String.class, a)).isEqualTo("ADMIN_CREATED");
        db.update("INSERT INTO app_user_external_identities(user_id,provider,provider_subject) VALUES (?,'GOOGLE','sub-1')", a);
        // One Google account is one user; one user has one Google account; only known providers; no unknown user.
        assertThatThrownBy(() -> db.update("INSERT INTO app_user_external_identities(user_id,provider,provider_subject) VALUES (?,'GOOGLE','sub-1')", b))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO app_user_external_identities(user_id,provider,provider_subject) VALUES (?,'GOOGLE','sub-2')", a))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO app_user_external_identities(user_id,provider,provider_subject) VALUES (?,'FACEBOOK','sub-3')", b))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO app_user_external_identities(user_id,provider,provider_subject) VALUES (-1,'GOOGLE','sub-4')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        db.update("INSERT INTO app_user_email_verifications(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 day')", a, "a".repeat(64));
        assertThatThrownBy(() -> db.update("INSERT INTO app_user_email_verifications(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 day')", a, "b".repeat(64)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO app_user_email_verifications(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 day')", b, "a".repeat(64)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO app_user_email_verifications(user_id,token_hash,expires_at) VALUES (?,?,now() - interval '1 day')", b, "c".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE app_users SET registration_source='FACEBOOK' WHERE id=?", a))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        // A user that still has a link or a pending verification cannot be removed by accident.
        assertThatThrownBy(() -> db.update("DELETE FROM app_users WHERE id=?", a))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> form(String email, String password) {
        var body = new LinkedHashMap<String, Object>();
        body.put("firstName", " Somchai "); body.put("lastName", "Jaidee"); body.put("email", email);
        body.put("phoneNumber", "02 791 6000"); body.put("password", password); body.put("confirmPassword", password);
        return body;
    }

    private GoogleIdentity identity(String subject, String email, boolean verified, String hostedDomain, String given, String family) {
        return new GoogleIdentity(subject, email, verified, hostedDomain, given, family, given + " " + family);
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

    /** What {@code POST /api/v1/admin/users/{id}/approve} does to the row (that endpoint has its own test class). */
    private void approve(long userId) {
        assertThat(db.update("UPDATE app_users SET account_status='ACTIVE' WHERE id=? AND account_status='PENDING_APPROVAL'", userId)).isOne();
    }

    /** The token of the verification link in the one verification mail sent since the last clearInvocations. */
    private String sentToken(String email, String firstName) {
        var link = ArgumentCaptor.forClass(String.class);
        verify(mail).sendVerification(eq(email), eq(firstName), link.capture(), eq(Duration.ofHours(24)));
        assertThat(link.getValue()).startsWith("https://admin.example.test/verify-email?token=");
        return link.getValue().substring(link.getValue().indexOf("token=") + 6);
    }

    private List<String> roles(long userId) {
        return db.queryForList("SELECT r.role_code FROM app_user_roles ur JOIN app_roles r ON r.id=ur.role_id WHERE ur.user_id=?", String.class, userId);
    }

    private List<String> columns(String table) {
        return db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name=?", String.class, table);
    }

    private int count(String table, long userId) {
        return db.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Integer.class, userId);
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/login", Map.of("email", email, "password", password));
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(AUTH + path).contentType("application/json").content(json.writeValueAsBytes(body)));
    }

    private JsonNode body(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }
}
