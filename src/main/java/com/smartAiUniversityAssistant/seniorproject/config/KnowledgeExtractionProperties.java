package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 14C text extraction (KNOWLEDGE_EXTRACTION_* variables, spec file 47 section 5). A character is one Java char.
 * The timeout must also be shorter than app.knowledge.ingestion.stage-timeout; ExtractTextStage checks that at startup.
 */
@Validated
@ConfigurationProperties("app.knowledge.extraction")
public record KnowledgeExtractionProperties(@Min(1) @Max(10_000) int maxPages, @Min(10_000) @Max(50_000_000) int maxChars,
        @Min(2_000) @Max(200_000) int unitMaxChars, @NotNull Duration timeout, boolean pdfSortByPosition, String tempDir) {

    @AssertTrue(message = "timeout must be between 10 seconds and 30 minutes")
    public boolean isTimeoutValid() {
        return timeout != null && timeout.compareTo(Duration.ofSeconds(10)) >= 0 && timeout.compareTo(Duration.ofMinutes(30)) <= 0;
    }
}
