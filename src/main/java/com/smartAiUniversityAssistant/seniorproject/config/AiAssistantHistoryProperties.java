package com.smartAiUniversityAssistant.seniorproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 17: persistent conversation history in PostgreSQL (AI_HISTORY_ENABLED, spec file 58 section 5). Off: nothing is
 * stored or restored and the assistant behaves as in Step 10 (Redis only).
 */
@Validated
@ConfigurationProperties("app.ai.history")
public record AiAssistantHistoryProperties(boolean enabled) {}
