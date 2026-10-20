package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class EnrollmentAlreadyExistsException extends EnrollmentFailure {
    public EnrollmentAlreadyExistsException() {
        super(409, "ENROLLMENT_ALREADY_EXISTS", "The student is already enrolled in this course section.");
    }
}
