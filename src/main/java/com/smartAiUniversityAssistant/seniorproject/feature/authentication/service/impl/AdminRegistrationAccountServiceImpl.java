package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AdminRegistrationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.*;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spec file 61. Lock order in every method: the app_users row first, then its credential and verification rows.
 */
@Service
public class AdminRegistrationAccountServiceImpl implements AdminRegistrationAccountService {
    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final AppUserRoleRepository userRoles;
    private final AppUserCredentialsRepository credentials;
    private final AppUserEmailVerificationRepository verifications;
    private final AppUserExternalIdentityRepository identities;
    private final AdminRegistrationProperties properties;
    private final Clock clock;

    public AdminRegistrationAccountServiceImpl(AppUserRepository users, AppRoleRepository roles,
            AppUserRoleRepository userRoles, AppUserCredentialsRepository credentials,
            AppUserEmailVerificationRepository verifications, AppUserExternalIdentityRepository identities,
            AdminRegistrationProperties properties, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.userRoles = userRoles;
        this.credentials = credentials;
        this.verifications = verifications;
        this.identities = identities;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional(timeout = 15)
    public RegistrationOutcome register(PendingRegistrationCommand command) {
        Instant instant = clock.instant();
        LocalDateTime now = utc(instant);
        List<AppUser> existing = users.findAllByEmailIgnoreCaseForUpdate(command.email());
        if (existing.isEmpty()) {
            AppUser user = createUser(command.firstName(), command.lastName(), command.email(), command.phoneNumber(),
                    AppUser.PENDING_VERIFICATION, AppUser.SOURCE_SELF_FORM, null, command.passwordHash(), now);
            replaceVerification(user.getId(), command.tokenHash(), command.expiresAt(), instant);
            return new RegistrationOutcome(RegistrationKind.CREATED, user.getEmail(), user.getFirstName());
        }
        if (existing.size() != 1) return new RegistrationOutcome(RegistrationKind.UNAVAILABLE, command.email(), null);
        AppUser user = existing.get(0);
        if (pendingForm(user)) {
            // Nobody has proven this address yet, so the newest registration wins: its name, password and link
            // replace the earlier ones, and the earlier link stops working.
            user.setFirstName(command.firstName());
            user.setLastName(command.lastName());
            user.setPhoneNumber(command.phoneNumber());
            user.setUpdatedAt(now);
            replacePassword(user, command.passwordHash(), now);
            replaceVerification(user.getId(), command.tokenHash(), command.expiresAt(), instant);
            return new RegistrationOutcome(RegistrationKind.REPLACED, user.getEmail(), user.getFirstName());
        }
        if (awaitingApproval(user))
            return new RegistrationOutcome(RegistrationKind.AWAITING_APPROVAL, user.getEmail(), user.getFirstName());
        boolean active = !user.isDeleted() && AppUser.ACTIVE.equals(user.getAccountStatus());
        return new RegistrationOutcome(active ? RegistrationKind.ALREADY_ACTIVE : RegistrationKind.UNAVAILABLE,
                user.getEmail(), user.getFirstName());
    }

    @Override
    @Transactional(timeout = 15)
    public Optional<AccountContact> verifyEmail(String tokenHash) {
        Instant instant = clock.instant();
        Long userId = verifications.findUserIdByTokenHash(tokenHash).orElse(null);
        if (userId == null) return Optional.empty();
        AppUser user = users.findByIdForUpdate(userId).orElse(null);
        // Read under the user lock: a newer link may have replaced this one in the meantime.
        AppUserEmailVerification verification = verifications.findByUserId(userId).orElse(null);
        if (user == null || verification == null || !verification.getTokenHash().equals(tokenHash)
                || !verification.getExpiresAt().isAfter(instant)) return Optional.empty();
        verifications.delete(verification);
        // The account was verified, deleted or changed by a Super Admin since the link was sent.
        if (!pendingForm(user)) return Optional.empty();
        LocalDateTime now = utc(instant);
        // Spec file 62: a proven address is not enough, a Super Admin approves the registration.
        user.setAccountStatus(AppUser.PENDING_APPROVAL);
        user.setEmailVerifiedAt(now);
        user.setUpdatedAt(now);
        return Optional.of(new AccountContact(user.getEmail(), user.getFirstName()));
    }

    @Override
    @Transactional(timeout = 15)
    public Optional<AccountContact> renewVerification(String email, String tokenHash, Instant expiresAt) {
        List<AppUser> existing = users.findAllByEmailIgnoreCaseForUpdate(email);
        if (existing.size() != 1 || !pendingForm(existing.get(0))) return Optional.empty();
        AppUser user = existing.get(0);
        replaceVerification(user.getId(), tokenHash, expiresAt, clock.instant());
        return Optional.of(new AccountContact(user.getEmail(), user.getFirstName()));
    }

    @Override
    @Transactional(timeout = 15)
    public GoogleOutcome signInWithGoogle(GoogleIdentity identity, String email, Supplier<String> unusablePasswordHash,
                                          boolean allowCreate) {
        Instant instant = clock.instant();
        LocalDateTime now = utc(instant);
        AppUserExternalIdentity link = identities
                .findByProviderAndProviderSubject(AppUserExternalIdentity.GOOGLE, identity.subject()).orElse(null);
        if (link != null) {
            AppUser user = users.findByIdForUpdate(link.getUserId()).orElse(null);
            if (user != null && awaitingApproval(user)) return outcome(GoogleKind.PENDING_APPROVAL, user);
            if (user == null || user.isDeleted() || !AppUser.ACTIVE.equals(user.getAccountStatus()))
                return outcome(GoogleKind.NOT_ACTIVE, user);
            return outcome(GoogleKind.SIGNED_IN, user);
        }
        List<AppUser> existing = users.findAllByEmailIgnoreCaseForUpdate(email);
        if (existing.isEmpty()) {
            if (!allowCreate) return outcome(GoogleKind.REGISTRATION_DISABLED, null);
            // Spec file 62: the new account waits for a Super Admin's approval.
            AppUser user = createUser(firstName(identity, email), lastName(identity), email, null,
                    AppUser.PENDING_APPROVAL, AppUser.SOURCE_GOOGLE, now, unusablePasswordHash.get(), now);
            link(user, identity, instant);
            return outcome(GoogleKind.CREATED, user);
        }
        if (existing.size() != 1 || existing.get(0).isDeleted()) return outcome(GoogleKind.NOT_ACTIVE, null);
        AppUser user = existing.get(0);
        // The person owns the email (Google verified it), but the account already belongs to another Google account.
        if (identities.findByUserIdAndProvider(user.getId(), AppUserExternalIdentity.GOOGLE).isPresent())
            return outcome(GoogleKind.MISMATCH, user);
        if (AppUser.ACTIVE.equals(user.getAccountStatus())) {
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(now);
                user.setUpdatedAt(now);
            }
            link(user, identity, instant);
            return outcome(GoogleKind.LINKED, user);
        }
        if (awaitingApproval(user)) {
            // The address was proven with the link; the same person may also use Google once approved.
            link(user, identity, instant);
            return outcome(GoogleKind.PENDING_APPROVAL, user);
        }
        if (pendingForm(user)) {
            // The form registration was never verified, so its password may have been chosen by somebody who does
            // not own the address. Google has now proven the owner: the account waits for approval (spec file 62)
            // and that password is replaced by one nobody knows.
            user.setAccountStatus(AppUser.PENDING_APPROVAL);
            user.setEmailVerifiedAt(now);
            user.setUpdatedAt(now);
            replacePassword(user, unusablePasswordHash.get(), now);
            verifications.findByUserId(user.getId()).ifPresent(verifications::delete);
            link(user, identity, instant);
            return outcome(GoogleKind.ACTIVATED, user);
        }
        return outcome(GoogleKind.NOT_ACTIVE, user);
    }

    private static boolean awaitingApproval(AppUser user) {
        return !user.isDeleted() && AppUser.PENDING_APPROVAL.equals(user.getAccountStatus());
    }

    private static boolean pendingForm(AppUser user) {
        return !user.isDeleted() && AppUser.PENDING_VERIFICATION.equals(user.getAccountStatus())
                && AppUser.SOURCE_SELF_FORM.equals(user.getRegistrationSource());
    }

    private AppUser createUser(String firstName, String lastName, String email, String phoneNumber, String status,
            String source, LocalDateTime emailVerifiedAt, String passwordHash, LocalDateTime now) {
        AppRole role = roles.findByRoleCode(properties.defaultRole()).filter(AppRole::isActive)
                .orElseThrow(() -> new AuthenticationFailure(503, "REGISTRATION_UNAVAILABLE",
                        "Registration is temporarily unavailable."));
        var user = new AppUser();
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPhoneNumber(phoneNumber);
        user.setAccountStatus(status);
        user.setRegistrationSource(source);
        user.setEmailVerifiedAt(emailVerifiedAt);
        user.setDeleted(false);
        user.setCreatedAt(now);
        user = users.saveAndFlush(user);

        var assignment = new AppUserRole();
        assignment.setUser(user);
        assignment.setRole(role);
        assignment.setAssignedAt(now);
        userRoles.save(assignment);

        var credential = new AppUserCredentials();
        credential.setUser(user);
        credential.setPasswordHash(passwordHash);
        credential.setForcePasswordChange(false);
        credential.setPasswordChangedAt(now);
        credential.setFailedLoginAttempts(0);
        credential.setCreatedAt(now);
        credentials.save(credential);
        return user;
    }

    private void replacePassword(AppUser user, String passwordHash, LocalDateTime now) {
        AppUserCredentials credential = credentials.findByUserIdForUpdate(user.getId()).orElseGet(() -> {
            var created = new AppUserCredentials();
            created.setUser(user);
            created.setForcePasswordChange(false);
            created.setCreatedAt(now);
            return created;
        });
        credential.setPasswordHash(passwordHash);
        credential.setPasswordChangedAt(now);
        credential.setFailedLoginAttempts(0);
        credential.setLockedUntil(null);
        credential.setUpdatedAt(now);
        credentials.save(credential);
    }

    private void replaceVerification(Long userId, String tokenHash, Instant expiresAt, Instant now) {
        AppUserEmailVerification verification = verifications.findByUserId(userId).orElseGet(() -> {
            var created = new AppUserEmailVerification();
            created.setUserId(userId);
            return created;
        });
        verification.setTokenHash(tokenHash);
        verification.setExpiresAt(expiresAt);
        verification.setCreatedAt(now);
        verifications.save(verification);
    }

    private void link(AppUser user, GoogleIdentity identity, Instant now) {
        var link = new AppUserExternalIdentity();
        link.setUserId(user.getId());
        link.setProvider(AppUserExternalIdentity.GOOGLE);
        link.setProviderSubject(identity.subject());
        link.setLinkedAt(now);
        identities.saveAndFlush(link);
    }

    private static GoogleOutcome outcome(GoogleKind kind, AppUser user) {
        return user == null ? new GoogleOutcome(kind, null, null, null)
                : new GoogleOutcome(kind, user.getId(), user.getEmail(), user.getFirstName());
    }

    /** Google's given name, else its full name, else the part of the email before the "@". */
    private static String firstName(GoogleIdentity identity, String email) {
        String value = clean(identity.givenName());
        if (value == null) value = clean(identity.fullName());
        if (value == null) value = clean(email.substring(0, Math.max(1, email.indexOf('@'))));
        return value == null ? "-" : value;
    }

    /** Google's family name; "-" when the account has none (the column is required). */
    private static String lastName(GoogleIdentity identity) {
        String value = clean(identity.familyName());
        return value == null ? "-" : value;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() <= 100 ? cleaned : cleaned.substring(0, cleaned.offsetByCodePoints(0,
                Math.min(100, cleaned.codePointCount(0, cleaned.length())))).strip();
    }

    private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
