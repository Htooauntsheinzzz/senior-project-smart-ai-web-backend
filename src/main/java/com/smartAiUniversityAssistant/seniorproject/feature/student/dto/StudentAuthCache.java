package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import java.time.LocalDateTime;

// Cached copy of authentication state; PostgreSQL stays authoritative.
public record StudentAuthCache(Long studentId, String studentCode, String accountStatus, Boolean isDeleted,
        Boolean forcePasswordChange, LocalDateTime lockedUntil) {}
