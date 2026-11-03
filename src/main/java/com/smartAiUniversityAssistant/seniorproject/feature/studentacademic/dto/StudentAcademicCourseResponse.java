package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

public record StudentAcademicCourseResponse(Long courseId, String courseCode, String courseName, Integer creditHours,
        String description, Integer courseType, String courseTypeName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        String enrollmentStatus) {}
