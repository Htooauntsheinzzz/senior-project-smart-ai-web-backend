package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

public record CampusBuildingStatisticsResponse(long totalBuildings, long activeBuildings, long underRenovationBuildings,
        long inactiveBuildings, long totalRooms) {}
