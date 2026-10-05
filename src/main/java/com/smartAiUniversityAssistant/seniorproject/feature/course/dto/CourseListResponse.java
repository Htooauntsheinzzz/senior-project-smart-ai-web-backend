package com.smartAiUniversityAssistant.seniorproject.feature.course.dto;

import java.time.Instant;

public record CourseListResponse(Long id, String courseCode, String courseName, Integer creditHours,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        Long programId, String programCode, String programName,
        Long semesterId, String semesterNameTh, String semesterNameEn,
        Integer recommendedAcademicYear, Integer courseType, String courseTypeName,
        Integer maximumStudentsPerSection, int sectionCount, Boolean isActive,
        Instant createdAt, Instant updatedAt) {}
