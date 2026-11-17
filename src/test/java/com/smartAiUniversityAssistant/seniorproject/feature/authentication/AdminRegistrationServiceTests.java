package com.smartAiUniversityAssistant.seniorproject.feature.authentication;

import com.smartAiUniversityAssistant.seniorproject.config.AdminRegistrationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.AdminRegistrationAccountService.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl.AdminRegistrationServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticationThrottle;
import java.time.*;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Spec file 61: the switches and edge cases of self-registration that need no database. */
class AdminRegistrationServiceTests {
    private final AdminRegistrationAccountService accounts = mock(AdminRegistrationAccountService.class);
    private final PasswordPolicy passwords = mock(PasswordPolicy.class);
    private final AuthenticationThrottle throttle = mock(AuthenticationThrottle.class);
    private final GoogleIdTokenVerifier google = mock(GoogleIdTokenVerifier.class);
    private final AdminMailService mail = mock(AdminMailService.class);
    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final GoogleIdentity person = new GoogleIdentity("g-1", "Anan@rsu.ac.th", true, "rsu.ac.th", "Anan", "Suksan", "Anan Suksan");
    private final AdminRegisterRequest form = new AdminRegisterRequest("Somchai", "Jaidee", "somchai.j@rsu.ac.th", null,
            "a long passphrase for rsu", "a long passphrase for rsu");

    @Test
    void switchedOffRegistrationRefusesNewAccountsButKeepsExistingGoogleSignIns() {
        var service = service(false, "client-id");
        assertThatThrownBy(() -> service.register(form)).isInstanceOfSatisfying(AuthenticationFailure.class, e -> {
            assertThat(e.status()).isEqualTo(403);
            assertThat(e.code()).isEqualTo("REGISTRATION_DISABLED");
        });
        verifyNoInteractions(accounts, mail);

        when(google.verify("token")).thenReturn(person);
        // allowCreate=false is handed to the database step, which answers for an unknown person ...
        when(accounts.signInWithGoogle(eq(person), eq("anan@rsu.ac.th"), any(), eq(false)))
                .thenReturn(new GoogleOutcome(GoogleKind.REGISTRATION_DISABLED, null, null, null));
        assertThatThrownBy(() -> service.signInWithGoogle(new GoogleSignInRequest("token")))
                .isInstanceOfSatisfying(AuthenticationFailure.class, e -> assertThat(e.code()).isEqualTo("REGISTRATION_DISABLED"));
        // ... and signs a known person in as before.
        when(accounts.signInWithGoogle(eq(person), eq("anan@rsu.ac.th"), any(), eq(false)))
                .thenReturn(new GoogleOutcome(GoogleKind.SIGNED_IN, 7L, "anan@rsu.ac.th", "Anan"));
        service.signInWithGoogle(new GoogleSignInRequest("token"));
        verify(authentication).startSession(7L);
        verifyNoInteractions(mail);
    }

    @Test
    void googleSignInWithoutAClientIdIsNotConfigured() {
        assertThatThrownBy(() -> service(true, " ").signInWithGoogle(new GoogleSignInRequest("token")))
                .isInstanceOfSatisfying(AuthenticationFailure.class, e -> {
                    assertThat(e.status()).isEqualTo(503);
                    assertThat(e.code()).isEqualTo("GOOGLE_SIGN_IN_NOT_CONFIGURED");
                });
        verifyNoInteractions(google, accounts);
    }

    @Test
    void googleSignInNeedsAnAccountOfTheUniversityWorkspace() {
        // A personal Google account that was created with a university address: verified email, right domain, no "hd".
        when(google.verify("personal")).thenReturn(new GoogleIdentity("g-2", "anan@rsu.ac.th", true, null, "Anan", "Suksan", "Anan Suksan"));
        when(google.verify("other")).thenReturn(new GoogleIdentity("g-3", "anan@rsu.ac.th", true, "other.example", "Anan", "Suksan", "Anan Suksan"));
        for (String token : List.of("personal", "other"))
            assertThatThrownBy(() -> service(true, "client-id").signInWithGoogle(new GoogleSignInRequest(token)))
                    .isInstanceOfSatisfying(AuthenticationFailure.class, e -> {
                        assertThat(e.status()).isEqualTo(403);
                        assertThat(e.code()).isEqualTo("GOOGLE_ACCOUNT_NOT_IN_DOMAIN");
                        assertThat(e.getMessage()).contains("@rsu.ac.th");
                    });
        verifyNoInteractions(accounts, authentication, mail);

        // GOOGLE_OAUTH_HOSTED_DOMAIN names the Workspace domain when it is not the email domain.
        var service = service(true, "client-id", "other.example");
        when(accounts.signInWithGoogle(any(), eq("anan@rsu.ac.th"), any(), eq(true)))
                .thenReturn(new GoogleOutcome(GoogleKind.SIGNED_IN, 7L, "anan@rsu.ac.th", "Anan"));
        service.signInWithGoogle(new GoogleSignInRequest("other"));
        verify(authentication).startSession(7L);
        when(google.verify("token")).thenReturn(person);
        assertThatThrownBy(() -> service.signInWithGoogle(new GoogleSignInRequest("token")))
                .isInstanceOfSatisfying(AuthenticationFailure.class, e -> assertThat(e.code()).isEqualTo("GOOGLE_ACCOUNT_NOT_IN_DOMAIN"));
    }

    @Test
    void twoFirstSignInsOfOnePersonAtTheSameMomentBothSucceed() {
        when(google.verify("token")).thenReturn(person);
        when(accounts.signInWithGoogle(eq(person), eq("anan@rsu.ac.th"), any(), eq(true)))
                .thenThrow(new DataIntegrityViolationException("uk_app_users_email"))
                .thenReturn(new GoogleOutcome(GoogleKind.SIGNED_IN, 7L, "anan@rsu.ac.th", "Anan"));
        service(true, "client-id").signInWithGoogle(new GoogleSignInRequest("token"));
        verify(authentication).startSession(7L);
    }

    @Test
    void aNewGoogleAccountGetsAPasswordHashNobodyKnowsAndWaitsForApproval() {
        when(google.verify("token")).thenReturn(person);
        when(accounts.signInWithGoogle(eq(person), eq("anan@rsu.ac.th"), any(), eq(true))).thenAnswer(call -> {
            Supplier<String> hash = call.getArgument(2);
            String first = hash.get();
            // A real BCrypt hash, different every time, so the credential row is valid but no password matches it.
            assertThat(first).matches("\\$2[aby]\\$04\\$[./A-Za-z0-9]{53}").isNotEqualTo(hash.get());
            return new GoogleOutcome(GoogleKind.CREATED, 9L, "anan@rsu.ac.th", "Anan");
        });
        // Spec file 62: the account is saved, but no session is opened before a Super Admin approves it.
        assertThatThrownBy(() -> service(true, "client-id").signInWithGoogle(new GoogleSignInRequest("token")))
                .isInstanceOfSatisfying(AuthenticationFailure.class, failure -> {
                    assertThat(failure.status()).isEqualTo(403);
                    assertThat(failure.code()).isEqualTo("ACCOUNT_PENDING_APPROVAL");
                });
        verify(mail).sendAwaitingApproval("anan@rsu.ac.th", "Anan");
        verifyNoInteractions(authentication);
    }

    @Test
    void twoRegistrationsOfOneNewAddressAtTheSameMomentAnswerTheSame() {
        when(passwords.validNewInput(any())).thenReturn(true);
        when(accounts.register(any())).thenThrow(new DataIntegrityViolationException("uk_app_users_email"));
        var response = service(true, "client-id").register(form);
        assertThat(response.email()).isEqualTo("somchai.j@rsu.ac.th");
        assertThat(response.message()).startsWith("If this address can be registered");
        verifyNoInteractions(mail);
        // The mail limit of the address is counted before anything else is done for it.
        verify(throttle).check("admin-registration-mail", "somchai.j@rsu.ac.th", 3);
        assertThat(form.toString()).doesNotContain("passphrase", "somchai");
    }

    private AdminRegistrationServiceImpl service(boolean enabled, String clientId) { return service(enabled, clientId, ""); }

    private AdminRegistrationServiceImpl service(boolean enabled, String clientId, String hostedDomain) {
        var properties = new AdminRegistrationProperties(enabled, "rsu.ac.th", "ADMIN", Duration.ofHours(24),
                "http://localhost:3000/verify-email", "http://localhost:3000/login", 3,
                new AdminRegistrationProperties.Google(clientId, "https://www.googleapis.com/oauth2/v3/certs", hostedDomain));
        return new AdminRegistrationServiceImpl(properties, accounts, passwords, new BCryptPasswordEncoder(4), throttle, google,
                mail, authentication, Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC));
    }
}
