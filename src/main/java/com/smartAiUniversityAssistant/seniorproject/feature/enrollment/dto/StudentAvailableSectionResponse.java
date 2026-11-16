package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto.StudentCourseMeetingResponse;
import java.time.LocalDate;
import java.util.List;

/**
 * One Course Section a Student may request (spec file 43). enrollmentId and enrollmentStatus describe the token
 * Student's own Enrollment in this section and are null when there is none. blockedReason is null when canEnroll.
 * Only sections on the published timetable are listed; enrollmentDueDate is null when no deadline is set (spec file 44).
 */
public record StudentAvailableSectionResponse(Long courseSectionId, String sectionNumber,
        Long courseId, String courseCode, String courseName, Integer creditHours, String description,
        Integer courseType, String courseTypeName,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Long lecturerId, String lecturerName, String lecturerNameTh,
        Integer capacity, long enrolledCount, long seatsAvailable, boolean full,
        Long enrollmentId, String enrollmentStatus, boolean canEnroll, String blockedReason,
        LocalDate enrollmentDueDate, boolean enrollmentClosed,
        List<StudentCourseMeetingResponse> meetings) {}
