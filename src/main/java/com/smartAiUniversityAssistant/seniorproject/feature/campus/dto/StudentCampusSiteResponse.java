package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import java.math.BigDecimal;

public record StudentCampusSiteResponse(Long id, String code, String name, String description, String timezone,
        BigDecimal centerLatitude, BigDecimal centerLongitude) {}
