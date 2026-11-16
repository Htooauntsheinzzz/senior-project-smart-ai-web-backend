package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CampusOpeningHoursRequest(@NotNull @Size(max = 7) List<@NotNull @Valid CampusOpeningHourDto> hours) {}
