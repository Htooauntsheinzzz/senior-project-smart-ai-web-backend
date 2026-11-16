package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.AcademicTermStatus;
import jakarta.validation.constraints.NotNull;

public record AcademicTermStatusRequest(@NotNull AcademicTermStatus status) {}
