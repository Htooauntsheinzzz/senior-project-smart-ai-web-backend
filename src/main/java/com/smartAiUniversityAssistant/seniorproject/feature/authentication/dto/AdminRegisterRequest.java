package com.smartAiUniversityAssistant.seniorproject.feature.authentication.dto;

import jakarta.validation.constraints.*;

/** Spec file 61: the Admin web registration form. The email is trimmed and lower-cased. */
public record AdminRegisterRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) @Pattern(regexp = "^(?=.*[0-9])[0-9 +()\\-]+$") String phoneNumber,
        @NotEmpty String password,
        @NotEmpty String confirmPassword) {
    public AdminRegisterRequest {
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
        email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
        phoneNumber = phoneNumber == null || phoneNumber.isBlank() ? null : phoneNumber.strip();
    }
    @Override public String toString() { return "AdminRegisterRequest[REDACTED]"; }
}
