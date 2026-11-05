package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

/** Stored, declared statuses. These are not live occupancy. */
public record CampusRoomStatisticsResponse(long totalRooms, long available, long occupied, long reserved, long maintenance, long inactive, String basis) {}
