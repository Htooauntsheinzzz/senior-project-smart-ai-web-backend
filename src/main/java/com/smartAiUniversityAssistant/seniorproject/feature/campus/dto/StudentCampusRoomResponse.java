package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import java.util.List;

/** declaredStatus is what the Admin entered. It is not proof the room is free right now. */
public record StudentCampusRoomResponse(Long id, String roomNumber, String roomName, CampusNamedRef building, int floorNumber, CampusRoomType roomType,
        Integer capacity, CampusRoomStatus declaredStatus, List<String> facilities) {}
