package com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception;

public class AdminAccountProvisioningFailure extends RuntimeException {
    private final int status;
    private final String code;

    public AdminAccountProvisioningFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }
}
