package com.smartAiUniversityAssistant.seniorproject.feature.semester.dto;

import java.time.Instant;

public record SemesterResponse(Long id, String semesterNameTh, String semesterNameEn,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
