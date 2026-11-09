package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

/** Resolved through room -> building -> campus; only roomId is persisted. legacyRoomLabel is V30 text, unverified. */
public record TimetableLocation(Long campusId, String campusName, Long buildingId, String buildingCode, String buildingName,
        Long roomId, String roomNumber, String roomName, Integer floorNumber, Integer capacity, String legacyRoomLabel) {}
