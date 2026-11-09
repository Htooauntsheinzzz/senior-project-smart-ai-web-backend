package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

import java.util.List;

public record AcademicCourseSectionQueryResult(Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        String enrollmentStatus, List<AcademicMeetingQueryResult> meetings) {}
