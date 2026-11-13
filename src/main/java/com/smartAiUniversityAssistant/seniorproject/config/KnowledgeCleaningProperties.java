package com.smartAiUniversityAssistant.seniorproject.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Step 14D cleaning (KNOWLEDGE_CLEANING_* variables, spec file 48 section 5). thai-digits-to-ascii also applies to search
 * questions (spec file 52) and must be the same for both. Changing any value needs a FULL reprocess of every document.
 */
@ConfigurationProperties("app.knowledge.cleaning")
public record KnowledgeCleaningProperties(boolean unwrapLines, boolean removeRepeatedLines, boolean thaiDigitsToAscii) {}
