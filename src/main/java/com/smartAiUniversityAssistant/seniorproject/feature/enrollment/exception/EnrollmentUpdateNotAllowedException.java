package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class EnrollmentUpdateNotAllowedException extends EnrollmentFailure {
    public EnrollmentUpdateNotAllowedException() {
        super(409, "ENROLLMENT_UPDATE_NOT_ALLOWED", "Student and course section can only be changed while the enrollment is pending.");
    }
}
