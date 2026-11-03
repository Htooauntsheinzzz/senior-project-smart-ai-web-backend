package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 10 conversation context for the student assistant (AI_CONTEXT_* variables). Conversations live only in Redis
 * and expire ttlMinutes after their last completed turn; maxTurns USER/ASSISTANT pairs are kept and sent to the model,
 * maxCourseReferences bounds the remembered course list and maxBytes the serialized Redis value.
 */
@Validated
@ConfigurationProperties("app.ai.context")
public record AiAssistantContextProperties(boolean enabled, @Min(1) @Max(1440) int ttlMinutes,
        @Min(1) @Max(20) int maxTurns, @Min(1) @Max(100) int maxCourseReferences,
        @Min(4096) @Max(262144) int maxBytes) {

    public Duration ttl() { return Duration.ofMinutes(ttlMinutes); }
}
