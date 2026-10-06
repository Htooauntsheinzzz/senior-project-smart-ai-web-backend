package com.smartAiUniversityAssistant.seniorproject.feature.program.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProgramListResponse(Long id, String programCode, String programName, String degreeLevel,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        Integer durationYears, Integer totalCredits,
        @JsonProperty("isActive") boolean isActive) {}
