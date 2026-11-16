package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.time.*;

public record CampusBuildingListResponse(Long id, Long siteId, String buildingCode, String buildingName, BigDecimal areaSquareMeters,
        int totalFloors, long totalRooms, String openingHoursSummary, CampusBuildingStatus status, Instant createdAt, Instant updatedAt) {}
