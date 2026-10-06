package com.smartAiUniversityAssistant.seniorproject.feature.lecture.exception;

public class LectureAlreadyExistsException extends LectureFailure {
    public LectureAlreadyExistsException() { super(409, "LECTURE_ALREADY_EXISTS", "Lecture already exists in this department."); }
}
