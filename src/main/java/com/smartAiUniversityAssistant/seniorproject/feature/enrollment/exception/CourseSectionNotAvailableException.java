package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class CourseSectionNotAvailableException extends EnrollmentFailure {
    public CourseSectionNotAvailableException() {
        super(409, "COURSE_SECTION_NOT_AVAILABLE", "The selected course section is not open for enrollment.");
    }
}
