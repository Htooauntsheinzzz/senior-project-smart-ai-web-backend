package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.constraints.*;

public record CampusFacilityRequest(
        @NotBlank @Size(max = 60) String facilityCode,
        @NotBlank @Size(max = 150) String facilityName,
        @Size(max = 5000) String description,
        Boolean isActive) {}
