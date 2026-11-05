package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CampusFacilityIdsRequest(@NotNull @Size(max = 100) List<@NotNull @Positive Long> facilityIds) {}
