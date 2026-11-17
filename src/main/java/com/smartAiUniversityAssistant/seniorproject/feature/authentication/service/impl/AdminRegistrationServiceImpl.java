package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AdminRegistrationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.AppUser;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.AdminRegistrationAccountService.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticationThrottle;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Spec file 61. No transaction here: {@link AdminRegistrationAccountService} commits first, then the mail is sent,
 * so a mail server problem never rolls a registration back and no database lock is held while a mail is sent.
 */
@Service
public class AdminRegistrationServiceImpl implements AdminRegistrationService {
    private static final Logger LOG = LoggerFactory.getLogger(AdminRegistrationServiceImpl.class);
    private static final String ACCEPTED = "If this address can be registered, we have sent an email to it. "
            + "Open the link in that email to finish the registration.";
    private static final String MAIL_THROTTLE = "admin-registration-mail";

    private final AdminRegistrationProperties properties;
    private final AdminRegistrationAccountService accounts;
    private final PasswordPolicy passwords;
    private final PasswordEncoder encoder;
    private final AuthenticationThrottle throttle;
    private final GoogleIdTokenVerifier google;
    private final AdminMailService mail;
    private final AuthenticationService authentication;
    private final Clock clock;

    public AdminRegistrationServiceImpl(AdminRegistrationProperties properties, AdminRegistrationAccountService accounts,
            PasswordPolicy passwords, PasswordEncoder encoder, AuthenticationThrottle throttle,
            GoogleIdTokenVerifier google, AdminMailService mail, AuthenticationService authentication, Clock clock) {
        this.properties = properties;
        this.accounts = accounts;
        this.passwords = passwords;
        this.encoder = encoder;
        this.throttle = throttle;
        this.google = google;
        this.mail = mail;
        this.authentication = authentication;
        this.clock = clock;
    }

    @Override
    public AdminRegistrationResponse register(AdminRegisterRequest request) {
        if (!properties.enabled()) throw registrationDisabled();
        if (!allowedDomain(request.email())) throw domainNotAllowed(400);
        if (!request.password().equals(request.confirmPassword()))
            throw new AuthenticationFailure(400, "PASSWORD_CONFIRMATION_MISMATCH",
                    "The password confirmation does not match the password.");
        if (!passwords.validNewInput(request.password()))
            throw new AuthenticationFailure(400, "PASSWORD_POLICY_VIOLATION",
                    "Use a password with at least 15 Unicode characters and at most 72 UTF-8 bytes, without NUL.");
        throttle.check(MAIL_THROTTLE, request.email(), properties.mailPerAddress());
        // BCrypt runs before the transaction so the row locks are held briefly.
        String hash = encoder.encode(request.password());
        String token = TokenSupport.random(32);
        RegistrationOutcome outcome;
        try {
            outcome = accounts.register(new PendingRegistrationCommand(request.firstName(), request.lastName(),
                    request.email(), request.phoneNumber(), hash, TokenSupport.digest(token),
                    clock.instant().plus(properties.verificationTtl())));
        } catch (DataIntegrityViolationException e) {
            // Two registrations of the same new address at the same moment: the other one created the account
            // and sends its own mail.
            LOG.info("ADMIN_REGISTRATION_CONCURRENT");
            return new AdminRegistrationResponse(request.email(), ACCEPTED);
        }
        switch (outcome.kind()) {
            case CREATED, REPLACED -> mail.sendVerification(outcome.email(), outcome.firstName(), link(token),
                    properties.verificationTtl());
            case ALREADY_ACTIVE -> mail.sendAlreadyRegistered(outcome.email(), outcome.firstName());
            // Spec file 62: tells the person why nothing happens.
            case AWAITING_APPROVAL -> mail.sendAwaitingApproval(outcome.email(), outcome.firstName());
            case UNAVAILABLE -> { /* No mail: the account is deleted, suspended, inactive or locked. */ }
        }
        LOG.info("ADMIN_REGISTRATION outcome={}", outcome.kind());
        return new AdminRegistrationResponse(request.email(), ACCEPTED);
    }

    @Override
    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request) {
        if (!TokenSupport.canonical(request.token(), 32)) throw invalidVerificationToken();
        AccountContact account = accounts.verifyEmail(TokenSupport.digest(request.token()))
                .orElseThrow(AdminRegistrationServiceImpl::invalidVerificationToken);
        LOG.info("ADMIN_EMAIL_VERIFIED");
        mail.sendAwaitingApproval(account.email(), account.firstName());
        return new VerifyEmailResponse(account.email(), AppUser.PENDING_APPROVAL,
                "Your email is verified. Your registration is waiting for approval by an administrator.");
    }

    @Override
    public AdminRegistrationResponse resendVerification(ResendVerificationRequest request) {
        throttle.check(MAIL_THROTTLE, request.email(), properties.mailPerAddress());
        if (allowedDomain(request.email())) {
            String token = TokenSupport.random(32);
            accounts.renewVerification(request.email(), TokenSupport.digest(token),
                            clock.instant().plus(properties.verificationTtl()))
                    .ifPresent(account -> mail.sendVerification(account.email(), account.firstName(), link(token),
                            properties.verificationTtl()));
        }
        return new AdminRegistrationResponse(request.email(), ACCEPTED);
    }

    @Override
    public TokenResponse signInWithGoogle(GoogleSignInRequest request) {
        if (!properties.google().configured())
            throw new AuthenticationFailure(503, "GOOGLE_SIGN_IN_NOT_CONFIGURED", "Google sign-in is not configured.");
        GoogleIdentity identity = google.verify(request.idToken());
        String email = identity.email() == null ? "" : identity.email().strip().toLowerCase(Locale.ROOT);
        if (!identity.emailVerified())
            throw new AuthenticationFailure(403, "GOOGLE_EMAIL_NOT_VERIFIED", "Google has not verified this email address.");
        if (!allowedDomain(email)) throw domainNotAllowed(403);
        // Google's rule for a sign-in that is limited to one domain: the domain of the email proves nothing, the "hd"
        // claim does. A personal Google account that was created with a university address has no "hd".
        if (identity.hostedDomain() == null
                || !identity.hostedDomain().strip().equalsIgnoreCase(properties.googleHostedDomain()))
            throw new AuthenticationFailure(403, "GOOGLE_ACCOUNT_NOT_IN_DOMAIN", "Use the Google account of your @"
                    + properties.allowedEmailDomain() + " university mailbox. A personal Google account cannot sign in.");
        GoogleOutcome outcome = googleAccount(identity, email);
        LOG.info("ADMIN_GOOGLE_SIGN_IN outcome={}", outcome.kind());
        switch (outcome.kind()) {
            case NOT_ACTIVE -> throw new AuthenticationFailure(403, "ACCOUNT_NOT_ACTIVE", "This account cannot sign in.");
            case MISMATCH -> throw new AuthenticationFailure(409, "GOOGLE_ACCOUNT_MISMATCH",
                    "This account is linked to another Google account.");
            case REGISTRATION_DISABLED -> throw registrationDisabled();
            // Spec file 62: the account is saved and waits for a Super Admin; no session before the approval.
            case CREATED, ACTIVATED -> {
                mail.sendAwaitingApproval(outcome.email(), outcome.firstName());
                throw AuthenticationFailure.pendingApproval();
            }
            case PENDING_APPROVAL -> throw AuthenticationFailure.pendingApproval();
            case SIGNED_IN, LINKED -> { }
        }
        return authentication.startSession(outcome.userId());
    }

    private GoogleOutcome googleAccount(GoogleIdentity identity, String email) {
        try {
            return accounts.signInWithGoogle(identity, email, this::unusablePasswordHash, properties.enabled());
        } catch (DataIntegrityViolationException e) {
            // The same person signed in twice at the same moment for the first time; the account exists now.
            return accounts.signInWithGoogle(identity, email, this::unusablePasswordHash, properties.enabled());
        }
    }

    /** The hash of 32 random bytes that are thrown away: the account has a credential row, but no password matches. */
    private String unusablePasswordHash() { return encoder.encode(TokenSupport.random(32)); }

    private String link(String token) { return properties.verificationUrl() + "?token=" + token; }

    /** One "@", a plain local part, and exactly the configured domain (no sub-domains). */
    private boolean allowedDomain(String email) {
        if (email == null) return false;
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@')) return false;
        String local = email.substring(0, at);
        return local.matches("[a-z0-9._%+-]+") && !local.startsWith(".") && !local.endsWith(".")
                && !local.contains("..") && email.substring(at + 1).equals(properties.allowedEmailDomain());
    }

    private AuthenticationFailure domainNotAllowed(int status) {
        return new AuthenticationFailure(status, "EMAIL_DOMAIN_NOT_ALLOWED",
                "Only @" + properties.allowedEmailDomain() + " email addresses can register.");
    }

    private static AuthenticationFailure registrationDisabled() {
        return new AuthenticationFailure(403, "REGISTRATION_DISABLED", "Self-registration is switched off.");
    }

    private static AuthenticationFailure invalidVerificationToken() {
        return new AuthenticationFailure(400, "INVALID_VERIFICATION_TOKEN",
                "The verification link is invalid or has expired. Ask for a new one.");
    }
}
