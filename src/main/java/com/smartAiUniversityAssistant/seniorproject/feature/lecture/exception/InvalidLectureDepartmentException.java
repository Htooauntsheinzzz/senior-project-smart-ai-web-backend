package com.smartAiUniversityAssistant.seniorproject.feature.lecture.exception;

public class InvalidLectureDepartmentException extends LectureFailure {
    public InvalidLectureDepartmentException() {
        super(400, "INVALID_LECTURE_DEPARTMENT", "The selected department is inactive or does not belong to the selected faculty.");
    }
}
