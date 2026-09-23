package com.smartAiUniversityAssistant.seniorproject.security;
import java.time.Instant;
public record SessionSecuritySnapshot(long userId, String sessionId, String credentialRevision,
        String mode, Instant absoluteExpiresAt) {
    @Override public String toString() { return "SessionSecuritySnapshot[REDACTED]"; }
}
