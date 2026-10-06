package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AuthenticationProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.*;
import com.smartAiUniversityAssistant.seniorproject.feature.student.mapper.StudentMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.*;
import org.springframework.stereotype.Service;

/**
 * Mobile authentication facade. It holds no transaction itself so PostgreSQL work commits before
 * any Redis session is created or token issued.
 */
@Service
public class StudentAuthServiceImpl implements StudentAuthService {
    private static final String RSU_DOMAIN = "rsu.ac.th";
    private final StudentCredentialService credentials;
    private final StudentSessionService sessions;
    private final StudentProfileService profiles;
    private final StudentMapper mapper;
    private final JwtTokenService tokens;
    private final AuthenticationThrottle throttle;
    private final AuthenticationProperties properties;
    private final Clock clock;

    public StudentAuthServiceImpl(StudentCredentialService credentials, StudentSessionService sessions,
            StudentProfileService profiles, StudentMapper mapper, JwtTokenService tokens,
            AuthenticationThrottle throttle, AuthenticationProperties properties, Clock clock) {
        this.credentials = credentials;
        this.sessions = sessions;
        this.profiles = profiles;
        this.mapper = mapper;
        this.tokens = tokens;
        this.throttle = throttle;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public StudentRegisterResponse register(StudentRegisterRequest request) {
        if (!isRsuEmail(request.universityEmail())) throw new InvalidStudentUniversityEmailException();
        // BCrypt runs before the transaction so the student row lock is held briefly.
        String hash = credentials.encodeNewPassword(request.password(), request.confirmPassword());
        return mapper.registerResponse(credentials.completeRegistration(request, hash));
    }

    @Override
    public StudentLoginResponse login(StudentLoginRequest request) {
        throttle.check("student-login-identifier", request.universityEmail(), properties.throttle().loginPerIdentifier());
        var outcome = credentials.verifyLogin(request.universityEmail(), request.password());
        var issued = sessions.create(outcome.studentId());
        var jwt = tokens.issueStudent(outcome.studentId(), outcome.studentCode(), issued.sessionId(),
                outcome.forcePasswordChange(), expiry(issued.session()));
        var profile = profiles.profile(outcome.studentId());
        Instant now = clock.instant();
        return new StudentLoginResponse("Bearer", jwt.value(), seconds(now, jwt.expiresAt()),
                issued.refreshToken().value(), seconds(now, expiry(issued.session())),
                outcome.forcePasswordChange(), profile);
    }

    @Override
    public StudentRefreshResponse refresh(StudentRefreshRequest request) {
        var presented = StudentRefreshToken.parse(request.refreshToken());
        var session = sessions.find(presented.sessionId()).orElseThrow(InvalidRefreshTokenException::new);
        if (!MessageDigest.isEqual(session.refreshTokenHash().getBytes(StandardCharsets.UTF_8),
                presented.digest().getBytes(StandardCharsets.UTF_8))) {
            // A superseded token was replayed: treat the session as compromised.
            bestEffortInvalidate(session.studentId(), presented.sessionId());
            throw new InvalidRefreshTokenException();
        }
        StudentAuthCache state;
        try {
            // PostgreSQL decides; a stale Redis session never outlives a disabled or deleted account.
            state = profiles.requireRefreshEligible(session.studentId());
        } catch (StudentFailure e) {
            bestEffortInvalidate(session.studentId(), presented.sessionId());
            throw e;
        }
        var next = sessions.rotate(presented.sessionId(), session.studentId(), presented)
                .orElseThrow(InvalidRefreshTokenException::new);
        boolean restricted = Boolean.TRUE.equals(state.forcePasswordChange());
        var jwt = tokens.issueStudent(state.studentId(), state.studentCode(), presented.sessionId(), restricted,
                expiry(session));
        Instant now = clock.instant();
        return new StudentRefreshResponse("Bearer", jwt.value(), seconds(now, jwt.expiresAt()), next.value(),
                seconds(now, expiry(session)), restricted, profiles.profile(state.studentId()));
    }

    @Override
    public void logout(AuthenticatedStudent student, boolean allDevices) {
        if (allDevices) sessions.invalidateAll(student.studentId());
        else sessions.invalidate(student.studentId(), student.sessionId());
    }

    @Override
    public void changePassword(AuthenticatedStudent student, StudentChangePasswordRequest request) {
        credentials.changePassword(student.studentId(), request);
    }

    @Override
    public StudentMeResponse me(AuthenticatedStudent student) {
        return profiles.profile(student.studentId());
    }

    static boolean isRsuEmail(String email) {
        if (email == null) return false;
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        int at = normalized.indexOf('@');
        if (at <= 0 || at != normalized.lastIndexOf('@')) return false;
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);
        return local.matches("[a-z0-9._%+-]+") && !local.startsWith(".") && !local.endsWith(".")
                && !local.contains("..") && domain.equals(RSU_DOMAIN);
    }

    private void bestEffortInvalidate(long studentId, String sessionId) {
        try { sessions.invalidate(studentId, sessionId); } catch (StudentAuthSessionUnavailableException ignored) { }
    }

    private Instant expiry(StudentRefreshSession session) { return session.expiresAt().toInstant(ZoneOffset.UTC); }
    private long seconds(Instant from, Instant to) { return Math.max(0, Duration.between(from, to).getSeconds()); }
}
