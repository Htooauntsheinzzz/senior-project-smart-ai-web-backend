package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** Fields a student may edit on their own profile; code, email and account status stay admin-controlled. */
public record StudentProfileUpdateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 30) String phoneNumber,
        @Past LocalDate dateOfBirth,
        @NotNull @Positive Long facultyId,
        @NotNull @Positive Long departmentId,
        @NotNull @Positive Long programId,
        @Positive Long semesterId,
        @Min(1900) @Max(9999) Integer academicYear,
        @Min(1900) @Max(9999) Integer enrollmentYear) {
    public StudentProfileUpdateRequest {
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim();
    }
}
