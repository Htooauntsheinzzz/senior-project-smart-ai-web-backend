package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 15A: the assistant's knowledge search tool (AI_KNOWLEDGE_* variables, spec file 53 section 5). topK passages per
 * search, maxContextChars the largest sum of passage text one search returns, maxSearchesPerRequest the searches one chat
 * request may run. Whether the tool is active is decided by AssistantKnowledgeSettings only. Step 15B: ownProgramQuota
 * places of a search are kept for passages of the Student's own program (0 = off, a value above topK acts as topK) and
 * maxNeighborPassages continuations of a hit's section may be added (0 = off).
 */
@Validated
@ConfigurationProperties("app.ai.knowledge")
public record AiAssistantKnowledgeProperties(boolean enabled, @Min(1) @Max(10) int topK,
        @Min(1000) @Max(40000) int maxContextChars, @Min(1) @Max(5) int maxSearchesPerRequest,
        @Min(0) @Max(10) int ownProgramQuota, @Min(0) @Max(4) int maxNeighborPassages) {}
