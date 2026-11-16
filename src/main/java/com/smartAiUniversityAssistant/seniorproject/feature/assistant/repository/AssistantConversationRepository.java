package com.smartAiUniversityAssistant.seniorproject.feature.assistant.repository;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AssistantConversationContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Step 10 conversations: one JSON STRING per conversation at {@code student:assistant:conversation:<conversationId>},
 * expiring by Redis TTL (no cleanup job). Since Step 17 the history lives in PostgreSQL (assistant_conversations); this
 * value stays the short-term context. Writes are atomic: a new conversation is created
 * only if the key is absent, and a follow-up replaces the value only while the stored version is still the one the
 * request loaded. Redis failures surface as {@link org.springframework.dao.DataAccessException}.
 */
@Repository
public class AssistantConversationRepository {
    static final String KEY_PREFIX = "student:assistant:conversation:";
    // Compare-and-set on the stored version, then replace the value with a fresh TTL in the same atomic step.
    // -1: the key is gone (expired); 0: another turn was saved first (or the value is unreadable); 1: saved.
    private static final DefaultRedisScript<Long> SAVE_IF_VERSION = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if not current then return -1 end
            local ok, decoded = pcall(cjson.decode, current)
            if not ok or type(decoded) ~= 'table' or tostring(decoded['version']) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    public enum SaveResult { SAVED, CONFLICT, MISSING }

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public AssistantConversationRepository(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    /** Empty when the key does not exist or the stored value cannot be read as the current schema. */
    public Optional<AssistantConversationContext> findById(String conversationId) {
        String value = redis.opsForValue().get(key(conversationId));
        if (value == null) return Optional.empty();
        try {
            var context = json.readValue(value, AssistantConversationContext.class);
            if (context.schemaVersion() != AssistantConversationContext.SCHEMA_VERSION
                    || !conversationId.equals(context.conversationId()) || context.studentId() == null)
                return Optional.empty();
            return Optional.of(context);
        } catch (JacksonException malformed) {
            return Optional.empty();
        }
    }

    /** Stores a brand-new conversation; false if the id is already taken. */
    public boolean create(AssistantConversationContext context, Duration ttl) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(context.conversationId()), serialize(context), ttl));
    }

    public SaveResult saveIfVersionMatches(AssistantConversationContext context, long expectedVersion, Duration ttl) {
        Long result = redis.execute(SAVE_IF_VERSION, List.of(key(context.conversationId())),
                Long.toString(expectedVersion), serialize(context), Long.toString(ttl.toMillis()));
        if (result == null) throw new IllegalStateException("Conversation save returned no result");
        return result == 1L ? SaveResult.SAVED : result == 0L ? SaveResult.CONFLICT : SaveResult.MISSING;
    }

    /** Step 17: removes the context of a conversation the Student deleted. */
    public void delete(String conversationId) {
        redis.delete(key(conversationId));
    }

    public String serialize(AssistantConversationContext context) {
        return json.writeValueAsString(context);
    }

    public int serializedBytes(AssistantConversationContext context) {
        return serialize(context).getBytes(StandardCharsets.UTF_8).length;
    }

    static String key(String conversationId) { return KEY_PREFIX + conversationId; }
}
