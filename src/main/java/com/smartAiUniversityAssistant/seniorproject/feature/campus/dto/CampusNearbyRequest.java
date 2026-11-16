package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Coordinates travel in the body so they never appear in a URL. They are not stored. */
public record CampusNearbyRequest(
        @Positive Long siteId,
        BigDecimal latitude,
        BigDecimal longitude,
        @Size(max = 40) String group,
        @Positive Integer limit,
        Boolean accessibleOnly) {}
