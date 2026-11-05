package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.util.List;

public record StudentCampusLocationsResponse(long total, List<Item> items, String nextCursor) {
    public record Item(Long id, CampusLocationType locationType, Long siteId, String code, String name, String category, CampusMobileGroup mobileGroup,
            String description, BigDecimal latitude, BigDecimal longitude, String status, boolean isPopular, boolean canNavigate) {}
}
