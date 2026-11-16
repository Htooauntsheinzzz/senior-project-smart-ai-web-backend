package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.AcademicTermStatus;
import java.time.*;

public record AcademicTermResponse(Long id, Long semesterId, String semesterNameEn, String semesterNameTh, Integer semesterAcademicYear,
        String academicYearCode, String academicYearLabel, LocalDate startDate, LocalDate endDate, AcademicTermStatus status,
        String displayLabel, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
