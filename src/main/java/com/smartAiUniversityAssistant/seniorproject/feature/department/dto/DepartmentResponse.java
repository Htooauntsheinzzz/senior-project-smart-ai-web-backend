package com.smartAiUniversityAssistant.seniorproject.feature.department.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record DepartmentResponse(Long id, String departmentCode, String departmentName,
        Long facultyId, String facultyCode, String facultyNameEn,
        long programCount, long courseCount, long studentCount,
        @JsonProperty("isActive") boolean isActive,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
