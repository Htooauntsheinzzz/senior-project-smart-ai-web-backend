package com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception;

public class AdminAccountManagementFailure extends RuntimeException {
    private final int status;
    private final String code;

    public AdminAccountManagementFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
