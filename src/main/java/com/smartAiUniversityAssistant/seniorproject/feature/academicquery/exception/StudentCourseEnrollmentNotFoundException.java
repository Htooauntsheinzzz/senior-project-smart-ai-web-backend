package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception;

public class StudentCourseEnrollmentNotFoundException extends AcademicQueryFailure {
    public StudentCourseEnrollmentNotFoundException() {
        super(404, "STUDENT_COURSE_ENROLLMENT_NOT_FOUND", "The student has no active enrollment in this course.");
    }
}
