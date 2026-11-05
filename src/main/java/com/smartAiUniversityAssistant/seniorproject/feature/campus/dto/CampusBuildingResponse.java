package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record CampusBuildingResponse(Long id, Long siteId, String siteName, String buildingCode, String buildingName, String buildingNameTh,
        String description, int totalFloors, BigDecimal areaSquareMeters, long totalRooms, String openingHoursSummary,
        List<CampusOpeningHourDto> openingHours, List<CampusOpeningExceptionDto> openingExceptions, CampusBuildingStatus status,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
