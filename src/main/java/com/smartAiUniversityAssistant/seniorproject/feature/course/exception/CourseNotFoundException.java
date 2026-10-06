package com.smartAiUniversityAssistant.seniorproject.feature.course.exception;

public class CourseNotFoundException extends CourseFailure {
    public CourseNotFoundException() { super(404, "COURSE_NOT_FOUND", "The requested course does not exist."); }
}
