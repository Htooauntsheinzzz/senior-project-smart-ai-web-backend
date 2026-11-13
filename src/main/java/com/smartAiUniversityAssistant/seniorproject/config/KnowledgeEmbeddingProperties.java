package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 14F embeddings (AI_EMBEDDING_* variables, spec file 50 section 5). Empty provider, base-url and api-key fall back
 * to the chat settings (AiAssistantProperties); the fallback is resolved in code because Compose passes unset variables
 * as empty strings. The model has no default: without it embeddings and search are not configured. dimensions is fixed
 * by the vector(768) column (pgvector HNSW indexes allow at most 2000 dimensions, so 3072 cannot be stored as is).
 * truncateLongerVectors keeps the first {@code dimensions} numbers of a longer vector and re-normalizes it; this is the
 * Matryoshka reduction used by gemini-embedding-001 and OpenAI text-embedding-3, and covers a provider that ignores the
 * dimensions field (gemini-embedding-001 returns 3072 numbers by default).
 */
@Validated
@ConfigurationProperties("app.knowledge.embedding")
public record KnowledgeEmbeddingProperties(String provider, String baseUrl, String apiKey, @Size(max = 100) String model,
        @Min(768) @Max(768) int dimensions, boolean sendDimensions, boolean truncateLongerVectors, @Min(1) @Max(100) int batchSize,
        @Min(500) @Max(300_000) int maxBatchTokens, @Min(1) @Max(120) int timeoutSeconds, @Min(1) @Max(60) int queryTimeoutSeconds,
        @Min(1) @Max(60) int connectTimeoutSeconds, @Min(0) @Max(5) int maxRetries, @NotNull Duration retryBackoff,
        @Min(0) @Max(60_000) int minIntervalMs) {

    public static final int DIMENSIONS = 768;

    @AssertTrue(message = "base-url must be an http(s) URL without credentials, query or fragment")
    public boolean isBaseUrlSafe() { return safeUrl(baseUrl); }

    @AssertTrue(message = "retry-backoff must be between 0 and 30 seconds")
    public boolean isRetryBackoffSafe() {
        return retryBackoff == null || (!retryBackoff.isNegative() && retryBackoff.compareTo(Duration.ofSeconds(30)) <= 0);
    }

    public String effectiveBaseUrl(AiAssistantProperties chat) { return hasText(baseUrl) ? baseUrl.strip() : blankToNull(chat.baseUrl()); }
    public String effectiveApiKey(AiAssistantProperties chat) { return hasText(apiKey) ? apiKey.strip() : blankToNull(chat.apiKey()); }
    public String effectiveProvider(AiAssistantProperties chat) { return hasText(provider) ? provider.strip() : chat.provider(); }

    /** Model, effective base URL and effective API key all have text. */
    public boolean configured(AiAssistantProperties chat) {
        return hasText(model) && hasText(effectiveBaseUrl(chat)) && hasText(effectiveApiKey(chat));
    }

    // The key must never reach logs or messages through toString().
    @Override
    public String toString() {
        return "KnowledgeEmbeddingProperties[provider=" + provider + ", baseUrl=" + baseUrl + ", apiKey="
                + (hasText(apiKey) ? "****" : "<unset>") + ", model=" + model + ", dimensions=" + dimensions
                + ", sendDimensions=" + sendDimensions + ", truncateLongerVectors=" + truncateLongerVectors + ", batchSize=" + batchSize + ", maxBatchTokens=" + maxBatchTokens
                + ", timeoutSeconds=" + timeoutSeconds + ", queryTimeoutSeconds=" + queryTimeoutSeconds
                + ", connectTimeoutSeconds=" + connectTimeoutSeconds + ", maxRetries=" + maxRetries
                + ", retryBackoff=" + retryBackoff + ", minIntervalMs=" + minIntervalMs + "]";
    }

    static boolean safeUrl(String url) {
        if (!hasText(url)) return true;
        try {
            var uri = java.net.URI.create(url.strip());
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (RuntimeException e) { return false; }
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static String blankToNull(String value) { return hasText(value) ? value.strip() : null; }
}
