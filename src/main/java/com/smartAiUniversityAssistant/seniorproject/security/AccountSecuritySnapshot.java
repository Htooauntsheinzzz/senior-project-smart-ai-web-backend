package com.smartAiUniversityAssistant.seniorproject.security;
import java.util.Set;
public record AccountSecuritySnapshot(long userId, boolean eligible, String credentialRevision,
        boolean forcePasswordChange, Set<String> roles) {
    @Override public String toString() { return "AccountSecuritySnapshot[REDACTED]"; }
}
