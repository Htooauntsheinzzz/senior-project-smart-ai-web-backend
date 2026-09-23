package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.impl;
import com.smartAiUniversityAssistant.seniorproject.security.*;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository.RefreshSessionRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
@Service
public class AuthenticationSessionReaderImpl implements AuthenticationSessionReader {
    private final RefreshSessionRepository sessions;
    public AuthenticationSessionReaderImpl(RefreshSessionRepository sessions) { this.sessions=sessions; }
    public Optional<SessionSecuritySnapshot> read(long uid,String sid) {
        return sessions.read(uid,sid).map(s -> new SessionSecuritySnapshot(s.userId(),s.sessionId(),s.credentialRevision(),s.mode(),s.absoluteExpiresAt()));
    }
}
