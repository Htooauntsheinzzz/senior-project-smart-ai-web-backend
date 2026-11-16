package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto;

import java.time.*;

public record CurriculumResponse(Long id, Long programId, String programCode, String programName,
        Long departmentId, String departmentName, String versionCode, String versionName, String academicYearLabel,
        String description, LocalDate effectiveFrom, LocalDate effectiveTo, String status, long documentCount,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
