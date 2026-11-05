package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.util.List;

// schedule is the Course Section's free-text value, returned as stored without interpretation.
public record StudentAcademicScheduleResponse(Long enrollmentId, Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        String schedule, String room, Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        String enrollmentStatus, List<StudentCourseMeetingResponse> meetings) {}
