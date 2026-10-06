package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception;

public class CourseSectionAlreadyExistsException extends CourseSectionFailure {
    public CourseSectionAlreadyExistsException() {
        super(409, "COURSE_SECTION_ALREADY_EXISTS", "Course section already exists for this course and semester.");
    }
}
