package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception;

public class InvalidCourseSectionLecturerException extends CourseSectionFailure {
    public InvalidCourseSectionLecturerException() {
        super(400, "INVALID_COURSE_SECTION_LECTURER",
                "The selected lecturer is inactive or does not belong to the course faculty and department.");
    }
}
