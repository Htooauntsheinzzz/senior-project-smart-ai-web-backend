package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

/** Spec file 61: checks a Google ID token (signature, issuer, audience, expiry) and reads the identity from it. */
public interface GoogleIdTokenVerifier {
    /**
     * @throws com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception.AuthenticationFailure
     *         401 INVALID_GOOGLE_TOKEN for a token that is not a valid Google ID token of this application,
     *         503 GOOGLE_SIGN_IN_UNAVAILABLE when Google's signing keys cannot be read
     */
    GoogleIdentity verify(String idToken);
}
