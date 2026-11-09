package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

import java.util.List;

// meetings is the published timetable of the section; there is no free-text schedule any more.
public record AcademicScheduleQueryResult(Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        List<AcademicMeetingQueryResult> meetings) {}
