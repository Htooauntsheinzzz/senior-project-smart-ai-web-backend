package com.smartAiUniversityAssistant.seniorproject.feature.course.exception;

public class CourseCodeAlreadyExistsException extends CourseFailure {
    public CourseCodeAlreadyExistsException() { super(409, "COURSE_CODE_ALREADY_EXISTS", "The course code is already in use."); }
}
