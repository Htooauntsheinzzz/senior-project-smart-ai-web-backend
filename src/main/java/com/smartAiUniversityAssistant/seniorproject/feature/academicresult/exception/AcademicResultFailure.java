package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.exception;

public class AcademicResultFailure extends RuntimeException {
    private final int status;
    private final String code;
    public AcademicResultFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
