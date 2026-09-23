package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto.ChangePasswordRequest;
public interface PasswordChangeService {
    enum Outcome { CHANGED, INVALID_CURRENT_PASSWORD, UNAUTHORIZED }
    Outcome change(AuthenticatedUser principal, ChangePasswordRequest request);
}
