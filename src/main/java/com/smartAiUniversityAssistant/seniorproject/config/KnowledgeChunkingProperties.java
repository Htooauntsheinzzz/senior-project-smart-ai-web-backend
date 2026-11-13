package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 14E chunking (KNOWLEDGE_CHUNK_* variables, spec file 49 section 4). Sizes are Java chars. After a change, existing
 * documents keep their chunks until they are reprocessed with mode FULL.
 */
@Validated
@ConfigurationProperties("app.knowledge.chunking")
public record KnowledgeChunkingProperties(@Min(200) @Max(4000) int targetChars, @Max(8000) int maxChars, @Min(0) int minChars,
        @Min(0) int overlapChars, @Min(1) @Max(50_000) int maxChunks) {

    @AssertTrue(message = "chunking sizes must satisfy target <= max, min <= target/2, overlap <= target/4 and min + target + overlap <= max")
    public boolean isConsistent() {
        return maxChars >= targetChars && minChars <= targetChars / 2 && overlapChars <= targetChars / 4
                && minChars + targetChars + overlapChars <= maxChars;
    }
}
