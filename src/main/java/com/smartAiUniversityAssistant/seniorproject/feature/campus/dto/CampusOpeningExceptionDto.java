package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.time.*;

public record CampusOpeningExceptionDto(
        @NotNull LocalDate date,
        @NotNull Boolean closed,
        @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
        @JsonFormat(pattern = "HH:mm") LocalTime closesAt,
        @Size(max = 255) String reason) {}
