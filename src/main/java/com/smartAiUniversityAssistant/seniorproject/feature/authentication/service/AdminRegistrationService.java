package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;

/** Spec file 61: Admin self-registration with the registration form or with Google. */
public interface AdminRegistrationService {
    AdminRegistrationResponse register(AdminRegisterRequest request);

    VerifyEmailResponse verifyEmail(VerifyEmailRequest request);

    AdminRegistrationResponse resendVerification(ResendVerificationRequest request);

    /** Signs in with Google; the first sign-in of a person registers the account. */
    TokenResponse signInWithGoogle(GoogleSignInRequest request);
}
