package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.time.*;

public record CampusCategoryResponse(Long id, String categoryCode, String categoryName, CampusMobileGroup mobileGroup,
        String description, boolean isActive, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
