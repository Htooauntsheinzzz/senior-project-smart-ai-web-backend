package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentRefreshSession;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentAuthSessionUnavailableException;
import com.smartAiUniversityAssistant.seniorproject.feature.student.repository.StudentRefreshSessionRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.TokenSupport;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class StudentSessionServiceImpl implements StudentSessionService {
    private final StudentRefreshSessionRepository sessions;
    private final AuthenticationProperties properties;
    private final Clock clock;

    public StudentSessionServiceImpl(StudentRefreshSessionRepository sessions, AuthenticationProperties properties,
            Clock clock) {
        this.sessions = sessions;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public IssuedSession create(long studentId) {
        String sessionId = TokenSupport.random(16);
        var token = StudentRefreshToken.generate(sessionId);
        var now = LocalDateTime.ofInstant(clock.instant().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);
        var session = new StudentRefreshSession(studentId, token.digest(), now, now.plus(properties.session().refreshTtl()));
        try {
            sessions.create(sessionId, session);
        } catch (DataAccessException | IllegalStateException e) {
            throw new StudentAuthSessionUnavailableException();
        }
        return new IssuedSession(sessionId, token, session);
    }

    @Override
    public Optional<StudentRefreshSession> find(String sessionId) {
        try {
            return sessions.find(sessionId)
                    .filter(s -> s.expiresAt().isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        } catch (DataAccessException e) {
            throw new StudentAuthSessionUnavailableException();
        }
    }

    @Override
    public Optional<StudentRefreshToken> rotate(String sessionId, long studentId, StudentRefreshToken presented) {
        var next = StudentRefreshToken.generate(sessionId);
        try {
            return sessions.rotate(sessionId, studentId, presented.digest(), next.digest())
                    ? Optional.of(next) : Optional.empty();
        } catch (DataAccessException e) {
            throw new StudentAuthSessionUnavailableException();
        }
    }

    @Override
    public void invalidate(long studentId, String sessionId) {
        try {
            sessions.delete(studentId, sessionId);
        } catch (DataAccessException e) {
            throw new StudentAuthSessionUnavailableException();
        }
    }

    @Override
    public void invalidateAll(long studentId) {
        try {
            sessions.deleteAll(studentId);
        } catch (DataAccessException e) {
            throw new StudentAuthSessionUnavailableException();
        }
    }
}
