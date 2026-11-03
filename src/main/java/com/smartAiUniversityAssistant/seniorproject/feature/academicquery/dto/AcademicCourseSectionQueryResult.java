package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

public record AcademicCourseSectionQueryResult(Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        String room, String schedule, Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        String enrollmentStatus) {}
