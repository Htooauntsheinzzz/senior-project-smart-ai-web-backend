package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import jakarta.validation.constraints.*;

/** Create and update body; categoryCode is stored upper-case. */
public record DocumentCategoryRequest(
        @NotBlank @Size(max = 60) String categoryCode,
        @NotBlank @Size(max = 150) String categoryName,
        @Size(max = 5000) String description) {}
