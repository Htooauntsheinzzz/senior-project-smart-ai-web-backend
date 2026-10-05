package com.smartAiUniversityAssistant.seniorproject.feature.course.exception;

public class InvalidCourseTypeException extends CourseFailure {
    public InvalidCourseTypeException(String message) { super(400, "INVALID_COURSE_TYPE", message); }
}
