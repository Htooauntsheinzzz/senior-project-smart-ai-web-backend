package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.util.List;

public record CampusNearbyResponse(int total, List<Item> items) {
    /** walkingDistanceMeters is a calculated route; straightLineDistanceMeters is only labeled proximity. */
    public record Item(Long locationId, CampusLocationType locationType, String code, String name, String category, CampusMobileGroup mobileGroup,
            String status, BigDecimal walkingDistanceMeters, Integer estimatedWalkingMinutes, BigDecimal straightLineDistanceMeters, boolean canNavigate) {}
}
