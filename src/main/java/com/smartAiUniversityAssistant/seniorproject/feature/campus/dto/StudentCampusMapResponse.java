package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.util.List;

public record StudentCampusMapResponse(Site site, Bounds bounds, List<Marker> buildings, List<Marker> places, Graph navigationGraph) {
    public record Site(Long id, String name, BigDecimal centerLatitude, BigDecimal centerLongitude) {}
    public record Bounds(BigDecimal minLatitude, BigDecimal minLongitude, BigDecimal maxLatitude, BigDecimal maxLongitude) {}
    public record Marker(Long id, CampusLocationType locationType, String code, String name, String category, CampusMobileGroup mobileGroup,
            String status, boolean isPopular, BigDecimal latitude, BigDecimal longitude, boolean canNavigate) {}
    public record Graph(List<Node> nodes, List<Edge> edges) {}
    public record Node(Long id, String name, CampusNodeType nodeType, BigDecimal latitude, BigDecimal longitude, boolean isAccessible) {}
    public record Edge(Long id, Long startNodeId, Long endNodeId, BigDecimal distanceMeters, boolean isWheelchairAccessible,
            boolean isBidirectional, List<CampusCoordinate> geometry) {}
}
