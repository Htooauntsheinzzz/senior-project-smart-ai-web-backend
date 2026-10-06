package com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record FacultyResponse(Long id, String facultyCode, String facultyNameEn, String facultyNameTh,
        @JsonProperty("isActive") Boolean isActive, long departmentCount, long studentCount,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
