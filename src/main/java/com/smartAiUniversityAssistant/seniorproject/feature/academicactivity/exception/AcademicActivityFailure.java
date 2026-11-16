package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.exception;

public class AcademicActivityFailure extends RuntimeException {
    private final int status;
    private final String code;
    public AcademicActivityFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
