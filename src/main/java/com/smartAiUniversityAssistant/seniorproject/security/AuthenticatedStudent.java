package com.smartAiUniversityAssistant.seniorproject.security;
public record AuthenticatedStudent(long studentId, String studentCode, String sessionId, boolean forcePasswordChange) {
    @Override public String toString() { return "AuthenticatedStudent[REDACTED]"; }
}
