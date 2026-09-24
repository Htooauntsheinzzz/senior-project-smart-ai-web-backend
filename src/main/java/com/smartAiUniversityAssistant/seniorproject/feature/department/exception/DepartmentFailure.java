package com.smartAiUniversityAssistant.seniorproject.feature.department.exception;

public class DepartmentFailure extends RuntimeException {
    private final int status;
    private final String code;
    public DepartmentFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
