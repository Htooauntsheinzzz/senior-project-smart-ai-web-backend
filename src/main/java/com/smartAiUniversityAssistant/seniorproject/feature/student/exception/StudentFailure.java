package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentFailure extends RuntimeException {
    private final int status;
    private final String code;
    public StudentFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
