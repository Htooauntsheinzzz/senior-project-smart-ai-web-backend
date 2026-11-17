package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
public interface AuthenticationService {

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    /**
     * Spec file 61: opens a session for a user whose identity was already proven without a password (Google sign-in).
     * The account must be eligible exactly as for a password login (active, not deleted, not locked).
     */
    TokenResponse startSession(long userId);

    void logout(AuthenticatedUser principal);

    CurrentUserResponse me(AuthenticatedUser principal);

    void changePassword(AuthenticatedUser principal,ChangePasswordRequest request);
}
