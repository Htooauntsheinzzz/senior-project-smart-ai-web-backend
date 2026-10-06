package com.smartAiUniversityAssistant.seniorproject.feature.student.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentRefreshSession;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

/**
 * Redis refresh sessions: a HASH per session at {@code student:auth:session:<sessionId>} and a SET of
 * session IDs per student at {@code student:auth:sessions:<studentId>}. Redis failures surface as
 * {@link org.springframework.dao.DataAccessException}; callers decide whether they are fatal.
 */
@Repository
public class StudentRefreshSessionRepository {
    private static final String SESSION_PREFIX = "student:auth:session:";
    private static final String INDEX_PREFIX = "student:auth:sessions:";
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'studentId', ARGV[1], 'refreshTokenHash', ARGV[2],
                    'createdAt', ARGV[3], 'expiresAt', ARGV[4])
            redis.call('PEXPIREAT', KEYS[1], ARGV[5])
            redis.call('SADD', KEYS[2], ARGV[6])
            if redis.call('PEXPIRETIME', KEYS[2]) < tonumber(ARGV[5]) then
                redis.call('PEXPIREAT', KEYS[2], ARGV[5])
            end
            return 1
            """, Long.class);
    // Compare-and-set keeps concurrent refreshes of the same token from both succeeding.
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            local v = redis.call('HMGET', KEYS[1], 'studentId', 'refreshTokenHash')
            if v[1] ~= ARGV[1] or v[2] ~= ARGV[2] then return 0 end
            redis.call('HSET', KEYS[1], 'refreshTokenHash', ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public StudentRefreshSessionRepository(StringRedisTemplate redis) { this.redis = redis; }

    public void create(String sessionId, StudentRefreshSession session) {
        long expiresAtMillis = session.expiresAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        Long result = redis.execute(CREATE, List.of(sessionKey(sessionId), indexKey(session.studentId())),
                Long.toString(session.studentId()), session.refreshTokenHash(), session.createdAt().toString(),
                session.expiresAt().toString(), Long.toString(expiresAtMillis), sessionId);
        if (result == null || result != 1L) throw new IllegalStateException("Refresh session was not stored");
    }

    public Optional<StudentRefreshSession> find(String sessionId) {
        Map<Object, Object> values = redis.opsForHash().entries(sessionKey(sessionId));
        if (values == null || values.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new StudentRefreshSession(Long.parseLong((String) values.get("studentId")),
                    (String) values.get("refreshTokenHash"),
                    LocalDateTime.parse((String) values.get("createdAt")),
                    LocalDateTime.parse((String) values.get("expiresAt"))));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    public boolean rotate(String sessionId, long studentId, String expectedHash, String nextHash) {
        Long result = redis.execute(ROTATE, List.of(sessionKey(sessionId)), Long.toString(studentId), expectedHash, nextHash);
        return result != null && result == 1L;
    }

    public void delete(long studentId, String sessionId) {
        redis.delete(sessionKey(sessionId));
        redis.opsForSet().remove(indexKey(studentId), sessionId);
    }

    public void deleteAll(long studentId) {
        Set<String> sessionIds = redis.opsForSet().members(indexKey(studentId));
        var keys = new ArrayList<String>();
        if (sessionIds != null) sessionIds.forEach(id -> keys.add(sessionKey(id)));
        keys.add(indexKey(studentId));
        redis.delete(keys);
    }

    public Set<String> sessionIds(long studentId) {
        Set<String> members = redis.opsForSet().members(indexKey(studentId));
        return members == null ? Set.of() : members;
    }

    private String sessionKey(String sessionId) { return SESSION_PREFIX + sessionId; }
    private String indexKey(long studentId) { return INDEX_PREFIX + studentId; }
}
