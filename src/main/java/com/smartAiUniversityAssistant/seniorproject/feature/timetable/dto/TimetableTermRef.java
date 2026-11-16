package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.AcademicTermStatus;
import java.time.LocalDate;

public record TimetableTermRef(Long id, Long semesterId, String semesterName, String semesterNameTh, String academicYearCode,
        String academicYearLabel, LocalDate startDate, LocalDate endDate, AcademicTermStatus status) {}
