package com.smartAiUniversityAssistant.seniorproject.feature.course.exception;

public class CourseFailure extends RuntimeException {
    private final int status;
    private final String code;
    public CourseFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
