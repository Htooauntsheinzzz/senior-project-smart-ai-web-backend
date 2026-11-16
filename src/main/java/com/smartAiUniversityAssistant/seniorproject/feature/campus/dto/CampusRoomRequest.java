package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.constraints.*;
import java.util.List;

public record CampusRoomRequest(
        @NotNull @Positive Long buildingId,
        @NotBlank @Size(max = 50) String roomNumber,
        @Size(max = 200) String roomName,
        @NotNull Integer floorNumber,
        @NotNull CampusRoomType roomType,
        @Positive Integer capacity,
        CampusRoomStatus status,
        @Size(max = 100) List<@NotNull @Positive Long> facilityIds) {}
