package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class EnrollmentNotFoundException extends EnrollmentFailure {
    public EnrollmentNotFoundException() {
        super(404, "ENROLLMENT_NOT_FOUND", "The requested enrollment does not exist.");
    }
}
