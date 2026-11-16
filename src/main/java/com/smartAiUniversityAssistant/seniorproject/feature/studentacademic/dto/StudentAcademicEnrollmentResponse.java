package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.time.LocalDate;
import java.util.List;

// meetings is the section's published timetable (spec 40); the legacy room/schedule text no longer exists.
// description is the course description, added last for the mobile My Courses cards (spec file 43, D62).
public record StudentAcademicEnrollmentResponse(Long enrollmentId, LocalDate enrollmentDate, String enrollmentStatus,
        Long courseId, String courseCode, String courseName, Integer creditHours, Integer courseType,
        Long courseSectionId, String sectionNumber,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Long lecturerId, String lecturerNameEn, String lecturerNameTh,
        List<StudentCourseMeetingResponse> meetings, String description) {}
