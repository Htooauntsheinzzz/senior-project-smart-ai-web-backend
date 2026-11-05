package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CampusRoutePreviewRequest(
        @Positive Long siteId,
        CampusCoordinate origin,
        @NotNull @Valid Destination destination,
        Boolean accessibleOnly) {
    public record Destination(@NotNull CampusLocationType type, @NotNull @Positive Long id) {}
}
