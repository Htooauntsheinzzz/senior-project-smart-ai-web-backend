package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Step 13 knowledge document storage (KNOWLEDGE_* variables). Files live under storage.root, outside the web root and
 * outside PostgreSQL; only "local" storage exists so far. deleteOrphans lets the startup reconciliation remove stored
 * files that no metadata row references (otherwise they are only reported).
 */
@Validated
@ConfigurationProperties("app.knowledge")
public record KnowledgeStorageProperties(boolean uploadEnabled, @Min(1) @Max(50) int maxFileSizeMb,
        @Valid @NotNull Storage storage) {

    public record Storage(@NotBlank @Pattern(regexp = "local") String type, @NotBlank String root, boolean deleteOrphans) {}

    public long maxFileSizeBytes() { return maxFileSizeMb * 1024L * 1024L; }
}
