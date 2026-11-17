package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Spec file 61: the database work of Admin self-registration. Every method is one transaction; mail, password
 * hashing of a form registration and Google token checks happen outside (see {@link AdminRegistrationService}).
 */
public interface AdminRegistrationAccountService {
    record PendingRegistrationCommand(String firstName, String lastName, String email, String phoneNumber,
                                      String passwordHash, String tokenHash, Instant expiresAt) {
        @Override public String toString() { return "PendingRegistrationCommand[REDACTED]"; }
    }

    enum RegistrationKind {
        /** A new account waiting for its email verification. */
        CREATED,
        /** The address already had an unverified registration; the newest details and link replace the old ones. */
        REPLACED,
        /** The address already has an active account. */
        ALREADY_ACTIVE,
        /** Spec file 62: the address has a registration that waits for a Super Admin's approval. */
        AWAITING_APPROVAL,
        /** The address belongs to an account that is deleted, suspended, inactive or locked. */
        UNAVAILABLE
    }
    record RegistrationOutcome(RegistrationKind kind, String email, String firstName) {}

    record AccountContact(String email, String firstName) {}

    enum GoogleKind {
        /** The Google account was already linked. */
        SIGNED_IN,
        /** An existing active account with the same email was linked. */
        LINKED,
        /** An unverified form registration with the same email was verified and linked; it now waits for approval. */
        ACTIVATED,
        /** A new account was created; it waits for approval (spec file 62). */
        CREATED,
        /** The account waits for a Super Admin's approval (spec file 62); it was linked if it was not yet. */
        PENDING_APPROVAL,
        /** The account is deleted, suspended, inactive or locked. */
        NOT_ACTIVE,
        /** The account is linked to another Google account. */
        MISMATCH,
        /** No account exists and self-registration is switched off. */
        REGISTRATION_DISABLED
    }
    record GoogleOutcome(GoogleKind kind, Long userId, String email, String firstName) {}

    RegistrationOutcome register(PendingRegistrationCommand command);

    /**
     * Verifies the email the token was sent to; the account then waits for approval (spec file 62). Empty when the
     * token is unknown, replaced, expired or already used.
     */
    Optional<AccountContact> verifyEmail(String tokenHash);

    /** Replaces the verification link of an unverified form registration. Empty when the address has none. */
    Optional<AccountContact> renewVerification(String email, String tokenHash, Instant expiresAt);

    /**
     * @param email                the lower-case email of the verified Google identity
     * @param unusablePasswordHash supplies the hash of a random password nobody knows, for an account without a password
     * @param allowCreate          false: an unknown person is not registered
     */
    GoogleOutcome signInWithGoogle(GoogleIdentity identity, String email, Supplier<String> unusablePasswordHash,
                                   boolean allowCreate);
}
