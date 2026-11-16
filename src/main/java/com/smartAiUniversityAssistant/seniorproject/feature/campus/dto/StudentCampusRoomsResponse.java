package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import java.util.List;

public record StudentCampusRoomsResponse(long total, List<StudentCampusRoomResponse> items) {}
