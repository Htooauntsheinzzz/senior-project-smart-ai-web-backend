package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception;

public class InvalidCourseSectionStatusException extends CourseSectionFailure {
    public InvalidCourseSectionStatusException(String message) {
        super(400, "INVALID_COURSE_SECTION_STATUS", message);
    }
}
