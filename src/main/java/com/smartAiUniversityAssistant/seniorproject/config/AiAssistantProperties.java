package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cloud LLM settings, supplied only through AI_* environment variables (or the git-ignored .env file).
 * The application starts without a key; chat requests then fail with AI_ASSISTANT_UNAVAILABLE.
 */
@Validated
@ConfigurationProperties("app.ai")
public record AiAssistantProperties(@NotBlank String provider, String apiKey, String model, String baseUrl,
        @Min(1) @Max(120) int timeoutSeconds, @Min(1) @Max(60) int connectTimeoutSeconds,
        @DecimalMin("0.0") @DecimalMax("2.0") double temperature, @Min(1) @Max(32768) int maxOutputTokens,
        @Min(0) @Max(2) int maxRetries, @NotNull Duration retryBackoff,
        @Min(1) @Max(10) int maxToolRounds, @Min(1) @Max(32) int maxToolCalls,
        @Min(1) @Max(600) int maxRequestSeconds) {

    public boolean configured() {
        return hasText(apiKey) && hasText(model) && hasText(baseUrl);
    }

    @AssertTrue(message = "base-url must be an http(s) URL without credentials, query or fragment")
    public boolean isBaseUrlSafe() {
        if (!hasText(baseUrl)) return true;
        try {
            var uri = java.net.URI.create(baseUrl.strip());
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (RuntimeException e) { return false; }
    }

    // The whole chat request (every provider call and tool run) is bounded, not only each provider call.
    @AssertTrue(message = "max-request-seconds must be at least timeout-seconds")
    public boolean isRequestBudgetSafe() {
        return maxRequestSeconds >= timeoutSeconds;
    }

    @AssertTrue(message = "retry-backoff must be between 0 and 10 seconds")
    public boolean isRetryBackoffSafe() {
        return retryBackoff == null || (!retryBackoff.isNegative() && retryBackoff.compareTo(Duration.ofSeconds(10)) <= 0);
    }

    // The key must never reach logs, actuator output or exception messages through toString().
    @Override
    public String toString() {
        return "AiAssistantProperties[provider=" + provider + ", apiKey=" + (hasText(apiKey) ? "****" : "<unset>")
                + ", model=" + model + ", baseUrl=" + baseUrl + ", timeoutSeconds=" + timeoutSeconds
                + ", connectTimeoutSeconds=" + connectTimeoutSeconds + ", temperature=" + temperature
                + ", maxOutputTokens=" + maxOutputTokens + ", maxRetries=" + maxRetries + ", retryBackoff=" + retryBackoff
                + ", maxToolRounds=" + maxToolRounds + ", maxToolCalls=" + maxToolCalls
                + ", maxRequestSeconds=" + maxRequestSeconds + "]";
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
}
