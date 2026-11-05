package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto;

import jakarta.validation.constraints.*;

public record CourseResultCreateRequest(@NotNull @Positive Long enrollmentId, @NotNull @Positive Long gradeDefinitionId) {}
