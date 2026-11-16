package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.util.List;

public record StudentCampusPlaceResponse(Long id, Long siteId, String code, String name, String nameTh, String category, CampusMobileGroup mobileGroup,
        String description, CampusNamedRef building, String floorLabel, String phoneNumber, CampusPlaceStatus status, boolean isPopular,
        List<CampusOpeningHourDto> openingHours, CampusOpeningStatus openingStatus, List<String> facilities,
        BigDecimal latitude, BigDecimal longitude, boolean canNavigate, boolean canViewRooms) {}
