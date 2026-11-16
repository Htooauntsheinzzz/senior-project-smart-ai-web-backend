package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CampusBuildingCreateRequest(
        @NotNull @Positive Long siteId,
        @NotBlank @Size(max = 50) String buildingCode,
        @NotBlank @Size(max = 200) String buildingName,
        @Size(max = 200) String buildingNameTh,
        @Size(max = 5000) String description,
        @NotNull @Positive @Max(200) Integer totalFloors,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal areaSquareMeters,
        CampusBuildingStatus status) {}
