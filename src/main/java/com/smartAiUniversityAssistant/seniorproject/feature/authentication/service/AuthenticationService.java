package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
public interface AuthenticationService {

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    void logout(AuthenticatedUser principal);

    CurrentUserResponse me(AuthenticatedUser principal);

    void changePassword(AuthenticatedUser principal,ChangePasswordRequest request);
}
