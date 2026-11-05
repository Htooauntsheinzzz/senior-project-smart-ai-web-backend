package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record DocumentCategoryResponse(Long id, String categoryCode, String categoryName, String description,
        @JsonProperty("isActive") boolean isActive, long documentCount,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
