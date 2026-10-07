package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class InvalidEnrollmentStatusException extends EnrollmentFailure {
    public InvalidEnrollmentStatusException() {
        super(400, "INVALID_ENROLLMENT_STATUS", "Enrollment status must be one of PENDING, ACTIVE, WITHDRAWN, DROPPED.");
    }
}
