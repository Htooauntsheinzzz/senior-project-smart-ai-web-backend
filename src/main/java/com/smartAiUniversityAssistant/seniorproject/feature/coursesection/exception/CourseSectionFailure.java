package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.exception;

public class CourseSectionFailure extends RuntimeException {
    private final int status;
    private final String code;
    public CourseSectionFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
