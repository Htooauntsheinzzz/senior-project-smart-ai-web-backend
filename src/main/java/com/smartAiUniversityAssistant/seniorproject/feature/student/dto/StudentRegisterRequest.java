package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * Mobile registration. firstName and lastName are required only when the student is new to the system;
 * a pre-provisioned PENDING profile already has them.
 */
public record StudentRegisterRequest(
        @NotBlank @Size(max = 50) String studentCode,
        @NotBlank @Email @Size(max = 255) String universityEmail,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 30) String phoneNumber,
        @Past LocalDate dateOfBirth,
        @NotBlank String password,
        @NotBlank String confirmPassword) {
    public StudentRegisterRequest {
        studentCode = studentCode == null ? null : studentCode.trim();
        universityEmail = universityEmail == null ? null : universityEmail.trim().toLowerCase(java.util.Locale.ROOT);
        firstName = firstName == null || firstName.isBlank() ? null : firstName.trim();
        lastName = lastName == null || lastName.isBlank() ? null : lastName.trim();
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.trim();
    }
    @Override public String toString() { return "StudentRegisterRequest[REDACTED]"; }
}
