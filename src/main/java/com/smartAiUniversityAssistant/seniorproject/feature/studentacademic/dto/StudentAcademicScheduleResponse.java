package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.util.List;

// meetings is the section's published timetable (spec 40).
public record StudentAcademicScheduleResponse(Long enrollmentId, Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        String enrollmentStatus, List<StudentCourseMeetingResponse> meetings) {}
