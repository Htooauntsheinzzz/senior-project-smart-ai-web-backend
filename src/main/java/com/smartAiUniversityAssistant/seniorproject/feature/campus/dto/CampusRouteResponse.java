package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record CampusRouteResponse(String routeId, String status, Destination destination, boolean accessibleOnly, BigDecimal distanceMeters,
        BigDecimal walkingNetworkDistanceMeters, BigDecimal approachDistanceMeters, int estimatedWalkingMinutes, Instant estimatedArrivalAt,
        CampusNamedRef startNode, CampusNamedRef arrivalNode, String geometryStatus, List<CampusCoordinate> geometry,
        List<OverviewSegment> overview, List<Instruction> instructions, Instant calculatedAt) {
    /** A room route stops at a building entrance: there is no indoor routing. */
    public record Destination(CampusLocationType type, Long id, String name, Building building, Integer floorNumber, String note) {}
    public record Building(Long id, String name) {}
    public record OverviewSegment(String edgeCode, String fromNode, String toNode, BigDecimal distanceMeters, CampusSurfaceType surfaceType,
            boolean isWheelchairAccessible, boolean hasMeasuredGeometry) {}
    public record Instruction(String text, BigDecimal distanceMeters, Integer bearingDegrees, String direction, String segment) {}
}
