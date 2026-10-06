package com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record FacultyListResponse(Long id, String facultyCode, String facultyNameEn, String facultyNameTh,
        long departmentCount, long studentCount, @JsonProperty("isActive") Boolean isActive,
        Instant createdAt) {}
