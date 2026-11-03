package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.time.LocalDate;

public record StudentAcademicEnrollmentResponse(Long enrollmentId, LocalDate enrollmentDate, String enrollmentStatus,
        Long courseId, String courseCode, String courseName, Integer creditHours, Integer courseType,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        String room, String schedule) {}
