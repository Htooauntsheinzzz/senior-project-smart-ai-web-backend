package com.smartAiUniversityAssistant.seniorproject.feature.student.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentAuthCache;
import com.smartAiUniversityAssistant.seniorproject.feature.student.dto.StudentMeResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** JSON caches for student authentication state and the mobile profile. PostgreSQL remains authoritative. */
@Repository
public class StudentCacheRepository {
    private static final String AUTH_PREFIX = "student:auth:account:";
    private static final String PROFILE_PREFIX = "student:mobile:profile:";
    static final Duration AUTH_TTL = Duration.ofMinutes(5);
    static final Duration PROFILE_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public StudentCacheRepository(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public Optional<StudentAuthCache> findAuth(long studentId) {
        return read(AUTH_PREFIX + studentId, StudentAuthCache.class);
    }

    public void saveAuth(StudentAuthCache value) {
        redis.opsForValue().set(AUTH_PREFIX + value.studentId(), json.writeValueAsString(value), AUTH_TTL);
    }

    public Optional<StudentMeResponse> findProfile(long studentId) {
        return read(PROFILE_PREFIX + studentId, StudentMeResponse.class);
    }

    public void saveProfile(StudentMeResponse value) {
        redis.opsForValue().set(PROFILE_PREFIX + value.id(), json.writeValueAsString(value), PROFILE_TTL);
    }

    public void evict(long studentId) {
        redis.delete(List.of(AUTH_PREFIX + studentId, PROFILE_PREFIX + studentId));
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        String value = redis.opsForValue().get(key);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(json.readValue(value, type));
        } catch (tools.jackson.core.JacksonException malformed) {
            redis.delete(key);
            return Optional.empty();
        }
    }
}
