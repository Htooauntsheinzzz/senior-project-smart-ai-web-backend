package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.math.BigDecimal;
import java.util.List;

public record StudentCampusBuildingResponse(Long id, Long siteId, String code, String name, String nameTh, String description, int totalFloors,
        List<CampusOpeningHourDto> openingHours, CampusOpeningStatus openingStatus, CampusBuildingStatus status,
        BigDecimal latitude, BigDecimal longitude, boolean canNavigate, long totalRooms, List<StudentCampusRoomResponse> rooms) {}
