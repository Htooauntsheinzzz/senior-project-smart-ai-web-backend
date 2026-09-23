package com.smartAiUniversityAssistant.seniorproject.exception;
import org.springframework.security.authentication.AuthenticationServiceException;
public class AuthenticationInfrastructureException extends AuthenticationServiceException {
    public AuthenticationInfrastructureException() { super("Authentication service temporarily unavailable."); }
}
