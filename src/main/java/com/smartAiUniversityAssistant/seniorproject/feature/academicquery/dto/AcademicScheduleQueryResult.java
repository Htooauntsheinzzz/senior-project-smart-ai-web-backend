package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

// schedule is the Course Section's free-text value, returned as stored without interpretation.
public record AcademicScheduleQueryResult(Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        String schedule, String room, Long lecturerId, String lecturerNameEn, String lecturerNameTh) {}
