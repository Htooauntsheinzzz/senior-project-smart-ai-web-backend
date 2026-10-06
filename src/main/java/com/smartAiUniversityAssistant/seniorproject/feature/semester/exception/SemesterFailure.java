package com.smartAiUniversityAssistant.seniorproject.feature.semester.exception;

public class SemesterFailure extends RuntimeException {
    private final int status;
    private final String code;
    public SemesterFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
