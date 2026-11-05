package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.dto;

import java.time.*;

public record CurriculumListResponse(Long id, Long programId, String programCode, String programName,
        String versionCode, String versionName, String academicYearLabel, LocalDate effectiveFrom,
        LocalDate effectiveTo, String status, long documentCount, Instant createdAt) {}
