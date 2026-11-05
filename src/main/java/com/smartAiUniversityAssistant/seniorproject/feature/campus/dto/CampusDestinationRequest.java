package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.constraints.*;

public record CampusDestinationRequest(
        @NotNull @Positive Long nodeId,
        @Positive Long buildingId,
        @Positive Long placeId,
        Boolean isPrimary,
        Boolean isAccessibleEntrance) {}
