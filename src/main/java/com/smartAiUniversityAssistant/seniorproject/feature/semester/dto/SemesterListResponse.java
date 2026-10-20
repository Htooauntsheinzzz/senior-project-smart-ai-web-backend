package com.smartAiUniversityAssistant.seniorproject.feature.semester.dto;

import java.time.Instant;

public record SemesterListResponse(Long id, Integer academicYear, String semesterNameTh, String semesterNameEn,
        Instant createdAt, Instant updatedAt) {}
