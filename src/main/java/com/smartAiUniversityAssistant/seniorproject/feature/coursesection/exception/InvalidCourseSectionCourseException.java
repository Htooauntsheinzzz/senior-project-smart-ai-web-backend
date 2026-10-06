package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception;

public class InvalidCourseSectionCourseException extends CourseSectionFailure {
    public InvalidCourseSectionCourseException() {
        super(400, "INVALID_COURSE_SECTION_COURSE", "The selected course is inactive.");
    }
}
