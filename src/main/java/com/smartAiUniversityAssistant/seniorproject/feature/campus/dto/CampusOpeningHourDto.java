package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.*;

public record CampusOpeningHourDto(
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
        @JsonFormat(pattern = "HH:mm") LocalTime closesAt,
        @NotNull Boolean closed) {}
