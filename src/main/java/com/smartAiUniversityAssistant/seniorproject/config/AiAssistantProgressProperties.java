package com.smartAiUniversityAssistant.seniorproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 16A: the assistant's academic progress tool (AI_ACADEMIC_PROGRESS_ENABLED, spec file 56 section 5). Step 16B:
 * hybridEnabled (AI_HYBRID_ANSWERS_ENABLED) lets a question about the Student's own standing combine the progress with
 * the university documents (spec file 57).
 */
@Validated
@ConfigurationProperties("app.ai.progress")
public record AiAssistantProgressProperties(boolean enabled, boolean hybridEnabled) {}
