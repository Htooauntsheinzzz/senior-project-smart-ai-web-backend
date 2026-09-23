package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
import java.time.Instant;
public record RefreshSession(long userId, String sessionId, String currentHash, String credentialRevision,
        String mode, Instant createdAt, Instant lastRotatedAt, Instant absoluteExpiresAt, int rotationCount) {
    @Override public String toString() { return "RefreshSession[REDACTED]"; }
}
