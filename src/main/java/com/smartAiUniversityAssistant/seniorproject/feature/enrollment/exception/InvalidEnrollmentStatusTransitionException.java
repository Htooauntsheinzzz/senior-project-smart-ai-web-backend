package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class InvalidEnrollmentStatusTransitionException extends EnrollmentFailure {
    public InvalidEnrollmentStatusTransitionException() {
        super(409, "INVALID_ENROLLMENT_STATUS_TRANSITION", "The requested enrollment status transition is not allowed.");
    }
}
