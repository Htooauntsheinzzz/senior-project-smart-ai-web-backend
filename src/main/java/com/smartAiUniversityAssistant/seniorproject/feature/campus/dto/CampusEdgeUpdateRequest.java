package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CampusEdgeUpdateRequest(
        @NotBlank @Size(max = 50) String edgeCode,
        @NotNull @Positive Long startNodeId,
        @NotNull @Positive Long endNodeId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal distanceMeters,
        @NotNull CampusSurfaceType surfaceType,
        Boolean isWheelchairAccessible,
        Boolean isBidirectional,
        CampusEdgeStatus status,
        @Size(max = 500) List<@NotNull CampusCoordinate> intermediatePoints) {}
