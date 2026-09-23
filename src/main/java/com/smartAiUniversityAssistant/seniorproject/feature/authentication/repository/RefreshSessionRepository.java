package com.smartAiUniversityAssistant.seniorproject.feature.authentication.repository;
import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.RefreshSession;
import java.time.Instant;
import java.util.Optional;
public interface RefreshSessionRepository {
    enum Rotation { ROTATED, INVALID, REPLAY, CONFLICT, REAUTHENTICATE }
    boolean create(RefreshSession session);
    Optional<RefreshSession> read(long userId, String sessionId);
    Rotation rotate(RefreshSession expected, String presentedHash, String newHash, String mode, Instant now);
    void delete(long userId, String sessionId);
}
