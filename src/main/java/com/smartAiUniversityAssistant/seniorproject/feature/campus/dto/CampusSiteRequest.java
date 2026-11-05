package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CampusSiteRequest(
        @NotBlank @Size(max = 50) String siteCode,
        @NotBlank @Size(max = 200) String siteName,
        @Size(max = 5000) String description,
        @Size(max = 60) String timezone,
        BigDecimal centerLatitude,
        BigDecimal centerLongitude,
        Boolean isActive) {}
