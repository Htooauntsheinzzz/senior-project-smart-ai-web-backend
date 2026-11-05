package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.constraints.*;

public record CampusPlacePopularRequest(@NotNull Boolean isPopular) {}
