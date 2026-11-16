package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

import java.time.LocalDate;
import java.util.List;

public record AcademicEnrollmentQueryResult(Long enrollmentId, String enrollmentStatus, LocalDate enrollmentDate,
        Long courseId, String courseCode, String courseName, Integer creditHours,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        List<AcademicMeetingQueryResult> meetings) {}
