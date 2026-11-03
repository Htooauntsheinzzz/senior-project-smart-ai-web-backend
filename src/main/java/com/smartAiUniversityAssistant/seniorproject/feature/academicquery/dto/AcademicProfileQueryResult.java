package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

// Flat view for AI tools. Placement fields are null for self-registered students who have not set them yet.
public record AcademicProfileQueryResult(Long studentId, String studentCode, String fullName, String universityEmail,
        Long facultyId, String facultyCode, String facultyNameEn, String facultyNameTh,
        Long departmentId, String departmentCode, String departmentName,
        Long programId, String programCode, String programName,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh,
        Integer academicYear, Integer enrollmentYear) {}
