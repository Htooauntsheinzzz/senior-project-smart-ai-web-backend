package com.smartAiUniversityAssistant.seniorproject.feature.student.service.impl;

import com.smartAiUniversityAssistant.seniorproject.feature.student.enums.StudentAccountStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.student.service.*;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class StudentAccessVerifierImpl implements StudentAccessVerifier {
    private final StudentSessionService sessions;
    private final StudentProfileService profiles;

    public StudentAccessVerifierImpl(StudentSessionService sessions, StudentProfileService profiles) {
        this.sessions = sessions;
        this.profiles = profiles;
    }

    @Override
    public Optional<AuthenticatedStudent> verify(long studentId, String sessionId) {
        if (!TokenSupport.canonical(sessionId, 16)) return Optional.empty();
        var session = sessions.find(sessionId).filter(s -> s.studentId() == studentId);
        if (session.isEmpty()) return Optional.empty();
        // is_deleted overrides every status; only ACTIVE students with a credential may act.
        return profiles.authState(studentId)
                .filter(state -> !Boolean.TRUE.equals(state.isDeleted())
                        && StudentAccountStatus.ACTIVE.name().equals(state.accountStatus()))
                .map(state -> new AuthenticatedStudent(studentId, state.studentCode(), sessionId,
                        Boolean.TRUE.equals(state.forcePasswordChange())));
    }
}
