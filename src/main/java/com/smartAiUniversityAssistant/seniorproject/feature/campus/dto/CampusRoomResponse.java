package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.time.*;
import java.util.List;

public record CampusRoomResponse(Long id, Long buildingId, String buildingCode, String buildingName, String roomNumber, String roomName,
        int floorNumber, CampusRoomType roomType, Integer capacity, CampusRoomStatus status, List<CampusFacilityRef> facilities,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
