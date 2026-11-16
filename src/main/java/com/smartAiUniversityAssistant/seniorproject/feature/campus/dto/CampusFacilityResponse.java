package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import java.time.*;

public record CampusFacilityResponse(Long id, String facilityCode, String facilityName, String description, boolean isActive,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
