package com.smartAiUniversityAssistant.seniorproject.feature.lecture.exception;

public class LectureNotFoundException extends LectureFailure {
    public LectureNotFoundException() { super(404, "LECTURE_NOT_FOUND", "The requested lecture does not exist."); }
}
