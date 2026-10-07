package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class CourseSectionFullException extends EnrollmentFailure {
    public CourseSectionFullException() {
        super(409, "COURSE_SECTION_FULL", "The selected course section has reached its capacity.");
    }
}
