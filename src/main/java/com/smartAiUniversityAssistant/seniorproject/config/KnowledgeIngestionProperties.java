package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Step 14B knowledge document ingestion worker (KNOWLEDGE_INGESTION_* variables, spec file 46 section 5). */
@Validated
@ConfigurationProperties("app.knowledge.ingestion")
public record KnowledgeIngestionProperties(boolean enabled, boolean schedulerEnabled, boolean autoQueue,
        @NotNull Duration pollInterval, @Min(1) @Max(10) int maxAttempts, @NotNull Duration retryInitialDelay,
        @DecimalMin(value = "0.0", inclusive = false) double retryMultiplier, @NotNull Duration retryMaxDelay,
        @NotNull Duration staleAfter, @NotNull Duration stageTimeout) {

    @AssertTrue(message = "poll-interval must be between 1 second and 5 minutes")
    public boolean isPollIntervalValid() { return between(pollInterval, Duration.ofSeconds(1), Duration.ofMinutes(5)); }

    @AssertTrue(message = "retry-initial-delay and retry-max-delay must be positive")
    public boolean isRetryDelayValid() {
        return retryInitialDelay != null && retryMaxDelay != null && retryInitialDelay.isPositive() && retryMaxDelay.isPositive();
    }

    @AssertTrue(message = "stale-after must be between 1 minute and 24 hours and greater than poll-interval")
    public boolean isStaleAfterValid() {
        return between(staleAfter, Duration.ofMinutes(1), Duration.ofHours(24)) && pollInterval != null && staleAfter.compareTo(pollInterval) > 0;
    }

    @AssertTrue(message = "stage-timeout must be between 1 minute and 12 hours")
    public boolean isStageTimeoutValid() { return between(stageTimeout, Duration.ofMinutes(1), Duration.ofHours(12)); }

    /** Delay before automatic retry n (n = attempts made so far): initial × multiplier^(n-1), capped at retry-max-delay. */
    public Duration retryDelay(int attempt) {
        double millis = retryInitialDelay.toMillis() * Math.pow(retryMultiplier, Math.max(0, attempt - 1));
        long capped = (long) Math.min(millis, (double) retryMaxDelay.toMillis());
        return Duration.ofMillis(capped);
    }

    private static boolean between(Duration value, Duration min, Duration max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }
}
