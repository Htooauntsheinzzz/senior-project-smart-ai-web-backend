package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

public record AcademicLecturerQueryResult(Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        String lecturerNickname, Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh) {}
