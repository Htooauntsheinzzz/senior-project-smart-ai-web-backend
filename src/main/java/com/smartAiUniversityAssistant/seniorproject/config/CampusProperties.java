package com.smartAiUniversityAssistant.seniorproject.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Walking estimates and bounds for campus navigation (spec file 41). */
@Validated
@ConfigurationProperties("app.campus")
public record CampusProperties(@Positive double walkingSpeedMetersPerSecond, @Positive double maxOriginSnapDistanceMeters,
        @Max(0) int lowestFloorNumber, @Min(1) int nearbyDefaultLimit, @Min(1) int nearbyMaxLimit) {}
