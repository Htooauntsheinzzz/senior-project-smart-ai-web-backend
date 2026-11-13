package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 14H semantic search (KNOWLEDGE_SEARCH_* variables, spec file 52 section 4). min-score 0.50 is an uncalibrated
 * starting value (calibrate it with the live evaluation of spec file 52, section 18). query-cache-ttl 0 turns the Redis
 * query-vector cache off.
 */
@Validated
@ConfigurationProperties("app.knowledge.search")
public record KnowledgeSearchProperties(@Min(1) @Max(10) int defaultTopK, @DecimalMin("0.0") @DecimalMax("1.0") double minScore,
        @Min(1) @Max(10) int maxPerDocument, @Min(50) @Max(1000) int hnswEfSearch, @NotNull Duration queryCacheTtl) {

    @AssertTrue(message = "query-cache-ttl must be between 0 and 7 days")
    public boolean isQueryCacheTtlValid() {
        return queryCacheTtl != null && !queryCacheTtl.isNegative() && queryCacheTtl.compareTo(Duration.ofDays(7)) <= 0;
    }
}
