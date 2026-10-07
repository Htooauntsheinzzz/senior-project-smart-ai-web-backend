package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import java.time.*;

public record EnrollmentListResponse(Long id,
        Long studentId, String studentCode, String studentName,
        Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        LocalDate enrollmentDate,
        Long semesterId, String semesterNameEn, String semesterNameTh,
        String status, Instant createdAt, Instant updatedAt) {}
