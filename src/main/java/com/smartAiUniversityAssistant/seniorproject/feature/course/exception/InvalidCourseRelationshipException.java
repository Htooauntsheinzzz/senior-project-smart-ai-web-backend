package com.smartAiUniversityAssistant.seniorproject.feature.course.exception;

public class InvalidCourseRelationshipException extends CourseFailure {
    public InvalidCourseRelationshipException(String message) { super(400, "INVALID_COURSE_RELATIONSHIP", message); }
}
