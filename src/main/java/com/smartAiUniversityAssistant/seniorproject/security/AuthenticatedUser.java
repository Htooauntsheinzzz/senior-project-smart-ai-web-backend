package com.smartAiUniversityAssistant.seniorproject.security;
import java.util.Set;
public record AuthenticatedUser(long userId, String sessionId, String credentialRevision,
        boolean forcePasswordChange, Set<String> roles) {
    @Override public String toString() { return "AuthenticatedUser[REDACTED]"; }
}
