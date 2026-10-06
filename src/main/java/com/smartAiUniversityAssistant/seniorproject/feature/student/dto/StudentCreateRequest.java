package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record StudentCreateRequest(
        @NotBlank @Size(max = 50) String studentCode,
        @NotBlank @Email @Size(max = 255) String universityEmail,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 30) String phoneNumber,
        @Past LocalDate dateOfBirth,
        @NotNull @Positive Long facultyId,
        @NotNull @Positive Long departmentId,
        @NotNull @Positive Long programId,
        @Positive Long semesterId,
        @Min(1900) @Max(9999) Integer academicYear,
        @Min(1900) @Max(9999) Integer enrollmentYear,
        @NotBlank String accountStatus,
        @NotBlank String temporaryPassword,
        @NotBlank String confirmPassword,
        Boolean forcePasswordChange) {
    public StudentCreateRequest {
        studentCode = studentCode == null ? null : studentCode.trim();
        universityEmail = universityEmail == null ? null : universityEmail.trim().toLowerCase(java.util.Locale.ROOT);
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim();
        accountStatus = accountStatus == null ? null : accountStatus.trim().toUpperCase(java.util.Locale.ROOT);
    }
    @Override public String toString() { return "StudentCreateRequest[REDACTED]"; }
}
