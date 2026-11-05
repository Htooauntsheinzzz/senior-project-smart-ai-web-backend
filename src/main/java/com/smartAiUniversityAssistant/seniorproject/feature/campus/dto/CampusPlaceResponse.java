package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.time.*;
import java.util.List;

public record CampusPlaceResponse(Long id, Long siteId, CampusPlaceResponse.Category category, CampusNamedRef building, String placeCode, String placeName,
        String placeNameTh, String description, String floorLabel, String phoneNumber, boolean isPopular, boolean isVisibleOnMobile,
        CampusPlaceStatus status, List<CampusFacilityRef> facilities, String openingHoursSummary,
        List<CampusOpeningHourDto> openingHours, List<CampusOpeningExceptionDto> openingExceptions,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {
    public record Category(Long id, String code, String name, CampusMobileGroup mobileGroup) {}
}
