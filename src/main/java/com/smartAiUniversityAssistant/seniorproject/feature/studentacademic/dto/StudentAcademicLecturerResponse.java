package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

// One row per Lecturer + Course Section, so the course each lecturer teaches stays visible.
public record StudentAcademicLecturerResponse(Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        String lecturerNickname, Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh) {}
