package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import java.time.Instant;

public record StudentListResponse(Long id, String studentCode, String universityEmail,
        String firstName, String lastName,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        Long programId, String programCode, String programName,
        Long semesterId, String semesterNameEn,
        Integer academicYear, Integer enrollmentYear,
        String accountStatus, Instant createdAt, Instant updatedAt) {}
