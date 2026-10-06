package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentAuthCache;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentMeResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.StudentCacheRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.*;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class StudentCacheServiceImpl implements StudentCacheService {
    private static final Logger LOG = LoggerFactory.getLogger(StudentCacheServiceImpl.class);
    private final StudentCacheRepository cache;
    private final StudentSessionService sessions;

    public StudentCacheServiceImpl(StudentCacheRepository cache, StudentSessionService sessions) {
        this.cache = cache;
        this.sessions = sessions;
    }

    @Override public Optional<StudentAuthCache> findAuth(long studentId) {
        try { return cache.findAuth(studentId); }
        catch (RuntimeException e) { warn("read auth", e); return Optional.empty(); }
    }

    @Override public void saveAuth(StudentAuthCache value) {
        try { cache.saveAuth(value); } catch (RuntimeException e) { warn("write auth", e); }
    }

    @Override public Optional<StudentMeResponse> findProfile(long studentId) {
        try { return cache.findProfile(studentId); }
        catch (RuntimeException e) { warn("read profile", e); return Optional.empty(); }
    }

    @Override public void saveProfile(StudentMeResponse value) {
        try { cache.saveProfile(value); } catch (RuntimeException e) { warn("write profile", e); }
    }

    @Override public void evictAfterCommit(long studentId) {
        afterCommit(() -> evict(studentId));
    }

    @Override public void revokeAfterCommit(long studentId) {
        afterCommit(() -> {
            // Refresh re-checks PostgreSQL, so a failed revocation cannot extend a disabled account's access.
            try { sessions.invalidateAll(studentId); } catch (RuntimeException e) { warn("revoke sessions", e); }
            evict(studentId);
        });
    }

    private void evict(long studentId) {
        try { cache.evict(studentId); } catch (RuntimeException e) { warn("evict", e); }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    private void warn(String operation, RuntimeException e) {
        LOG.warn("STUDENT_REDIS_{}_FAILED {}", operation.toUpperCase().replace(' ', '_'), e.getClass().getSimpleName());
    }
}
