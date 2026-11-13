package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeSearchProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.slf4j.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Query vectors in Redis (spec file 52, section 7) so the same question costs one provider request per query-cache-ttl.
 * Key: knowledge:search:query-vector: + SHA-256 of model, dimensions and the prepared question (the question itself is
 * never stored). Value: Base64 of the floats, 4 bytes each, big-endian. A Redis failure is a miss or an ignored write,
 * logged once at WARN without a stack trace; it never fails a search.
 */
@Repository
public class QueryEmbeddingCache {
    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingCache.class);
    static final String PREFIX = "knowledge:search:query-vector:";
    private final StringRedisTemplate redis;
    private final Duration ttl;

    @org.springframework.beans.factory.annotation.Autowired
    public QueryEmbeddingCache(StringRedisTemplate redis, KnowledgeSearchProperties properties) {
        this(redis, properties.queryCacheTtl());
    }

    public QueryEmbeddingCache(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    public boolean enabled() { return ttl != null && !ttl.isZero(); }

    public Optional<float[]> get(String model, int dimensions, String question) {
        if (!enabled()) return Optional.empty();
        String value;
        try {
            value = redis.opsForValue().get(key(model, dimensions, question));
        } catch (RuntimeException e) {
            log.warn("KNOWLEDGE_SEARCH_CACHE_FAILURE operation=read error={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
        return Optional.ofNullable(decode(value, dimensions));
    }

    public void put(String model, int dimensions, String question, float[] vector) {
        if (!enabled() || vector == null || vector.length != dimensions) return;
        try {
            redis.opsForValue().set(key(model, dimensions, question), encode(vector), ttl);
        } catch (RuntimeException e) {
            log.warn("KNOWLEDGE_SEARCH_CACHE_FAILURE operation=write error={}", e.getClass().getSimpleName());
        }
    }

    public static String key(String model, int dimensions, String question) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((model + "\n" + dimensions + "\n" + question).getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String encode(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4);
        for (float v : vector) buffer.putFloat(v);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /** null when the value is missing, not Base64, of the wrong length or not finite (a miss; the next write replaces it). */
    static float[] decode(String value, int dimensions) {
        if (value == null) return null;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (bytes.length != dimensions * 4) return null;
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vector[i] = buffer.getFloat();
            if (!Float.isFinite(vector[i])) return null;
        }
        return vector;
    }
}
