package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record CampusEdgeResponse(Long id, Long siteId, String edgeCode, CampusNamedRef startNode, CampusNamedRef endNode, BigDecimal distanceMeters,
        Integer walkTimeMinutes, CampusSurfaceType surfaceType, boolean isWheelchairAccessible, boolean isBidirectional, CampusEdgeStatus status,
        boolean hasGeometry, List<CampusCoordinate> intermediatePoints, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
