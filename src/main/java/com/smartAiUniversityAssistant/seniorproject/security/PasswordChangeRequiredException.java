package com.smartAiUniversityAssistant.seniorproject.security;
import org.springframework.security.access.AccessDeniedException;
public class PasswordChangeRequiredException extends AccessDeniedException {
    public PasswordChangeRequiredException() { super("Password change is required."); }
}
