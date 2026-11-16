package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.constraints.*;

public record CampusCategoryRequest(
        @NotBlank @Size(max = 60) String categoryCode,
        @NotBlank @Size(max = 150) String categoryName,
        @NotNull CampusMobileGroup mobileGroup,
        @Size(max = 5000) String description,
        Boolean isActive) {}
