package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

/**
 * Spec file 61: what a verified Google ID token says about the person.
 *
 * @param subject       Google's stable id of the account (the "sub" claim)
 * @param emailVerified whether Google has verified that the person owns the email address
 * @param hostedDomain  the Google Workspace domain of the account (the "hd" claim), null for other accounts
 */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String hostedDomain,
                             String givenName, String familyName, String fullName) {
    @Override public String toString() { return "GoogleIdentity[REDACTED]"; }
}
