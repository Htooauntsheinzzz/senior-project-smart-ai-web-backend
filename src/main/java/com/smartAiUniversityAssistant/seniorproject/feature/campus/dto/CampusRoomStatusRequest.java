package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.constraints.*;

public record CampusRoomStatusRequest(@NotNull CampusRoomStatus status) {}
