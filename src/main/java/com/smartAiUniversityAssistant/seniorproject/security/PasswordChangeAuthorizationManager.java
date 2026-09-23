package com.smartAiUniversityAssistant.seniorproject.security;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
@Component
public class PasswordChangeAuthorizationManager {
    public void requireFull(Authentication authentication) {
        if (authentication!=null && authentication.getPrincipal() instanceof AuthenticatedUser u && u.forcePasswordChange())
            throw new PasswordChangeRequiredException();
    }
}
