package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import com.smartAiUniversityAssistant.seniorproject.IntegrationSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.DeterministicEmbeddingClient;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.QueryEmbeddingCache;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.*;

/** Spec file 52, section 17.2: the query-vector cache against the Redis Testcontainer. */
class QueryEmbeddingCacheIntegrationTests extends IntegrationSupport {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private final float[] vector = DeterministicEmbeddingClient.vector("How many credits do I need to graduate?");

    static LettuceConnectionFactory factory(String host, int port) {
        var config = new RedisStandaloneConfiguration(host, port);
        config.setPassword("test-only-redis-password");
        var factory = new LettuceConnectionFactory(config, LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build());
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    @BeforeEach
    void connect() {
        factory = factory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.keys(QueryEmbeddingCache.key("x", 1, "x").substring(0, 30) + "*").forEach(redis::delete);
    }

    @AfterEach
    void close() { factory.destroy(); }

    @Test
    void storesReadsExpiresAndMisses() {
        var cache = new QueryEmbeddingCache(redis, Duration.ofHours(24));
        assertThat(cache.get("m", 768, "question")).isEmpty();
        cache.put("m", 768, "question", vector);
        assertThat(cache.get("m", 768, "question")).hasValueSatisfying(v -> assertThat(v).containsExactly(vector));
        assertThat(cache.get("other-model", 768, "question")).isEmpty();
        assertThat(cache.get("m", 768, "another question")).isEmpty();
        String key = QueryEmbeddingCache.key("m", 768, "question");
        assertThat(key).startsWith("knowledge:search:query-vector:").doesNotContain("question");
        assertThat(redis.getExpire(key)).isBetween(Duration.ofHours(23).toSeconds(), Duration.ofHours(24).toSeconds());
        assertThat(redis.opsForValue().get(key)).doesNotContain("question");

        redis.opsForValue().set(key, "not base64 !!");
        assertThat(cache.get("m", 768, "question")).isEmpty();
        redis.opsForValue().set(key, java.util.Base64.getEncoder().encodeToString(new byte[12]));
        assertThat(cache.get("m", 768, "question")).isEmpty();
        cache.put("m", 768, "question", vector);
        assertThat(cache.get("m", 768, "question")).isPresent();
    }

    @Test
    void zeroTtlNeitherReadsNorWrites() {
        var off = new QueryEmbeddingCache(redis, Duration.ZERO);
        off.put("m", 768, "q-off", vector);
        assertThat(redis.hasKey(QueryEmbeddingCache.key("m", 768, "q-off"))).isFalse();
        new QueryEmbeddingCache(redis, Duration.ofMinutes(5)).put("m", 768, "q-off", vector);
        assertThat(off.get("m", 768, "q-off")).isEmpty();
        assertThat(off.enabled()).isFalse();
    }

    @Test
    void unreachableRedisIsAMissAndAnIgnoredWrite() throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        var deadFactory = factory("127.0.0.1", port);
        try {
            var dead = new StringRedisTemplate(deadFactory);
            dead.afterPropertiesSet();
            var cache = new QueryEmbeddingCache(dead, Duration.ofHours(1));
            assertThat(cache.get("m", 768, "q")).isEmpty();
            assertThatCode(() -> cache.put("m", 768, "q", vector)).doesNotThrowAnyException();
        } finally {
            deadFactory.destroy();
        }
    }
}
