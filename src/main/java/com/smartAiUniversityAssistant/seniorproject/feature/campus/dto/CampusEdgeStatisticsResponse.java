package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

public record CampusEdgeStatisticsResponse(long totalNodes, long activeNodes, long totalEdges, long activeEdges, long inactiveEdges,
        long underConstructionEdges, long wheelchairAccessibleEdges, long oneWayEdges) {}
