package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import jakarta.validation.constraints.*;

// The Student always comes from the access token; the body only names the Course Section.
public record StudentEnrollRequest(@NotNull @Positive Long courseSectionId) {}
