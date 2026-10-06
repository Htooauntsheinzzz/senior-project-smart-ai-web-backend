package com.smartAiUniversityAssistant.seniorproject.feature.student.service;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentAuthCache;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentMeResponse;
import java.util.Optional;

/**
 * Redis side effects for student state. Reads fall back to PostgreSQL on any Redis failure, and
 * writes made inside a transaction run only after it commits.
 */
public interface StudentCacheService {
    Optional<StudentAuthCache> findAuth(long studentId);
    void saveAuth(StudentAuthCache value);
    Optional<StudentMeResponse> findProfile(long studentId);
    void saveProfile(StudentMeResponse value);

    /** Evicts the auth and mobile profile caches after commit. */
    void evictAfterCommit(long studentId);

    /** Revokes every refresh session and evicts caches after commit. */
    void revokeAfterCommit(long studentId);
}
