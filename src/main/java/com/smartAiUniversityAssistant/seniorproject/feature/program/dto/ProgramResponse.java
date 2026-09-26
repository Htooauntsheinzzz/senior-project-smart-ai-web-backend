package com.smartAiUniversityAssistant.seniorproject.feature.program.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ProgramResponse(Long id, String programCode, String programName, String degreeLevel,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        Integer durationYears, Integer totalCredits,
        @JsonProperty("isActive") boolean isActive,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
