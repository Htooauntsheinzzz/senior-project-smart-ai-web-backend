package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentRefreshSession;
import java.util.Optional;

public interface StudentSessionService {
    record IssuedSession(String sessionId, StudentRefreshToken refreshToken, StudentRefreshSession session) {}

    /** Creates a session; Redis failure is fatal and raises AUTH_SESSION_UNAVAILABLE. */
    IssuedSession create(long studentId);

    Optional<StudentRefreshSession> find(String sessionId);

    /** Atomically replaces the presented token; returns the new token or empty when the CAS lost. */
    Optional<StudentRefreshToken> rotate(String sessionId, long studentId, StudentRefreshToken presented);

    void invalidate(long studentId, String sessionId);

    void invalidateAll(long studentId);
}
