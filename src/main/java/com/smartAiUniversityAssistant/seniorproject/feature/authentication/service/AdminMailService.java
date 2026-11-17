package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import java.time.Duration;

/**
 * Spec files 61 and 62: the registration mails of the Admin web. A mail that cannot be sent never fails the request:
 * the methods return false and log the failure without the address or the content.
 */
public interface AdminMailService {
    /** The mail with the verification link of a form registration. */
    boolean sendVerification(String to, String firstName, String verificationLink, Duration validFor);

    /**
     * Spec file 62: the registration is received and the email proven; a Super Admin still has to approve it. Sent
     * when the link is used, after the first Google sign-in, and when a waiting address registers again.
     */
    boolean sendAwaitingApproval(String to, String firstName);

    /** Spec file 62: a Super Admin approved the registration; the person can sign in now. */
    boolean sendApproved(String to, String firstName);

    /** Spec file 62: a Super Admin rejected a registration whose email was proven. */
    boolean sendRejected(String to, String firstName);

    /** Sent instead of a verification mail when the address already has an active account. */
    boolean sendAlreadyRegistered(String to, String firstName);
}
