package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception;

public class AcademicQueryFailure extends RuntimeException {
    private final int status;
    private final String code;
    public AcademicQueryFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
