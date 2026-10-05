package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception;

public class CourseSectionNotFoundException extends CourseSectionFailure {
    public CourseSectionNotFoundException() {
        super(404, "COURSE_SECTION_NOT_FOUND", "The requested course section does not exist.");
    }
}
