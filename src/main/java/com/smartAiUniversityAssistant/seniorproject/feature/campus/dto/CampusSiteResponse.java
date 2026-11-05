package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import java.math.BigDecimal;
import java.time.*;

public record CampusSiteResponse(Long id, String siteCode, String siteName, String description, String timezone,
        BigDecimal centerLatitude, BigDecimal centerLongitude, boolean isActive,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
