package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception;

public class StudentAlreadyEnrolledInCourseException extends EnrollmentFailure {
    public StudentAlreadyEnrolledInCourseException() {
        super(409, "STUDENT_ALREADY_ENROLLED_IN_COURSE", "The student is already enrolled in another section of this course for the selected semester.");
    }
}
