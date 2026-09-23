package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;
import com.smartAiUniversityAssistant.seniorproject.security.AccountSecuritySnapshot;
import java.util.Optional;
public interface RefreshSessionService {
    record Prepared(RefreshSession session,RefreshToken token) {
        @Override public String toString() { return "Prepared[REDACTED]"; }
    }
    Prepared prepare(AccountSecuritySnapshot account);
    boolean create(RefreshSession session);
    Optional<RefreshSession> read(RefreshToken token);
    void rotate(RefreshSession session,RefreshToken presented,RefreshToken replacement,String mode);
    void delete(long userId,String sessionId);
}
