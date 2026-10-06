package com.smartAiUniversityAssistant.seniorproject.feature.course.exception;

public class InvalidPrerequisiteCourseException extends CourseFailure {
    public InvalidPrerequisiteCourseException(String message) { super(400, "INVALID_PREREQUISITE_COURSE", message); }
}
