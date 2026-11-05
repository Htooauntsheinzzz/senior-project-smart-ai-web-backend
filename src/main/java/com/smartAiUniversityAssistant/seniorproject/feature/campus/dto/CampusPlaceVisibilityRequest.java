package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.constraints.*;

public record CampusPlaceVisibilityRequest(@NotNull Boolean isVisibleOnMobile) {}
