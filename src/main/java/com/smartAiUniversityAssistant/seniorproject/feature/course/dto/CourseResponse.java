package com.smartAiUniversityAssistant.seniorproject.feature.course.dto;

import java.time.Instant;

public record CourseResponse(Long id, String courseCode, String courseName, Integer creditHours, String description,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        Long programId, String programCode, String programName,
        Long semesterId, String semesterNameTh, String semesterNameEn,
        Integer recommendedAcademicYear, String prerequisiteCourses,
        Integer courseType, String courseTypeName, Integer maximumStudentsPerSection,
        Boolean isActive, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
