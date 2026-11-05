package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.util.List;

public record CampusAdminMapResponse(Site site, List<Marker> buildings, List<Marker> places, List<NavigationNode> navigationNodes,
        List<NavigationEdge> navigationEdges, Summary summary) {
    public record Site(Long id, String name, BigDecimal centerLatitude, BigDecimal centerLongitude) {}
    /** Coordinates come only from a mapped destination node, so they are null until one is mapped. */
    public record Marker(Long id, CampusLocationType entityType, String code, String name, String category, String status,
            BigDecimal latitude, BigDecimal longitude, boolean canNavigate) {}
    public record NavigationNode(Long id, String entityType, String code, String name, CampusNodeType nodeType, BigDecimal latitude,
            BigDecimal longitude, boolean isAccessible, boolean isActive) {}
    public record NavigationEdge(Long id, String entityType, String code, Long startNodeId, Long endNodeId, BigDecimal distanceMeters,
            Integer walkTimeMinutes, CampusSurfaceType surfaceType, boolean isWheelchairAccessible, boolean isBidirectional,
            CampusEdgeStatus status, List<CampusCoordinate> geometry) {}
    public record Summary(long totalLocations, long activeLocations, long inactiveLocations, long locationsWithoutCoordinates) {}
}
