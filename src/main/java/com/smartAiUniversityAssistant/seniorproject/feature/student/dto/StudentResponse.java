package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import java.time.*;

public record StudentResponse(Long id, String studentCode, String universityEmail,
        String firstName, String lastName, String phoneNumber, LocalDate dateOfBirth,
        Long facultyId, String facultyCode, String facultyNameEn,
        Long departmentId, String departmentCode, String departmentName,
        Long programId, String programCode, String programName,
        Long semesterId, String semesterNameTh, String semesterNameEn,
        Integer academicYear, Integer enrollmentYear,
        String accountStatus, boolean forcePasswordChange,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
