package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class EnrollmentStudentNotEligibleException extends EnrollmentFailure {
    public EnrollmentStudentNotEligibleException() {
        super(409, "ENROLLMENT_STUDENT_NOT_ELIGIBLE", "The selected student is not eligible for enrollment.");
    }
}
