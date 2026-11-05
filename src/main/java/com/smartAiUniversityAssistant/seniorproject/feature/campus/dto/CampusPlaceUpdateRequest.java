package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.constraints.*;
import java.util.List;

public record CampusPlaceUpdateRequest(
        @NotNull @Positive Long categoryId,
        @Positive Long buildingId,
        @NotBlank @Size(max = 50) String placeCode,
        @NotBlank @Size(max = 200) String placeName,
        @Size(max = 200) String placeNameTh,
        @Size(max = 5000) String description,
        @Size(max = 60) String floorLabel,
        @Size(max = 50) String phoneNumber,
        Boolean isPopular,
        Boolean isVisibleOnMobile,
        CampusPlaceStatus status,
        @Size(max = 100) List<@NotNull @Positive Long> facilityIds) {}
