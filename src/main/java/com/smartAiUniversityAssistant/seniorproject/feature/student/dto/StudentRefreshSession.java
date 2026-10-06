package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import java.time.LocalDateTime;

public record StudentRefreshSession(Long studentId, String refreshTokenHash, LocalDateTime createdAt,
        LocalDateTime expiresAt) {
    @Override public String toString() { return "StudentRefreshSession[REDACTED]"; }
}
