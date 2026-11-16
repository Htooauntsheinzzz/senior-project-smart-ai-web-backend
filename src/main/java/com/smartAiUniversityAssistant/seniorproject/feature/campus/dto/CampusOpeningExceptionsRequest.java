package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CampusOpeningExceptionsRequest(@NotNull @Size(max = 366) List<@NotNull @Valid CampusOpeningExceptionDto> exceptions) {}
