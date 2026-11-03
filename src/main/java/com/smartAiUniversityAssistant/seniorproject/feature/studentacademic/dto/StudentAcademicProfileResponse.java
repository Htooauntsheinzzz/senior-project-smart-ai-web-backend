package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.time.LocalDate;

// Faculty, Department, Program and Semester are null for self-registered students who have not set them yet.
public record StudentAcademicProfileResponse(Long studentId, String studentCode, String firstName, String lastName,
        String fullName, String universityEmail, String phoneNumber, LocalDate dateOfBirth,
        AcademicReferenceResponse faculty, AcademicReferenceResponse department, AcademicReferenceResponse program,
        AcademicSemesterResponse semester, Integer academicYear, Integer enrollmentYear, String accountStatus) {}
