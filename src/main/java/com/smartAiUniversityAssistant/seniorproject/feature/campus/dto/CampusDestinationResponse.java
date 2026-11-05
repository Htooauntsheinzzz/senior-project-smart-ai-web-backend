package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import java.time.*;

public record CampusDestinationResponse(Long id, Long siteId, CampusNamedRef node, CampusNamedRef building, CampusNamedRef place,
        boolean isPrimary, boolean isAccessibleEntrance, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
