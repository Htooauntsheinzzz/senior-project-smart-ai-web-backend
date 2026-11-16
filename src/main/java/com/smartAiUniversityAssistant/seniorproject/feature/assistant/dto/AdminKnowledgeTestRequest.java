package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import jakarta.validation.constraints.*;

/**
 * Body of POST /api/v1/admin/assistant/knowledge-test (spec file 59). Only question is required; topK and minScore
 * default to the assistant's own values (AI_KNOWLEDGE_TOP_K, KNOWLEDGE_SEARCH_MIN_SCORE).
 */
public record AdminKnowledgeTestRequest(
        @NotBlank(message = "Question is required")
        @Size(max = 500, message = "Question must not exceed 500 characters")
        String question,
        @Min(1) @Max(10) Integer topK,
        @DecimalMin("0.0") @DecimalMax("1.0") Double minScore) {}
