package com.smartAiUniversityAssistant.seniorproject.feature.authentication.exception;

public class AuthenticationFailure extends RuntimeException {
    private final int status;
    private final String code;
    public AuthenticationFailure(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static AuthenticationFailure credentials() {
        return new AuthenticationFailure(401, "INVALID_CREDENTIALS", "Unable to authenticate with the supplied credentials.");
    }
    public static AuthenticationFailure refresh() {
        return new AuthenticationFailure(401, "INVALID_REFRESH_TOKEN", "Unable to refresh this session.");
    }
    public static AuthenticationFailure validation() {
        return new AuthenticationFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
    /** Spec file 62: a self-registered account that a Super Admin has not approved yet. */
    public static AuthenticationFailure pendingApproval() {
        return new AuthenticationFailure(403, "ACCOUNT_PENDING_APPROVAL",
                "Your registration is waiting for approval by an administrator.");
    }
    public static AuthenticationFailure unauthorized() {
        return new AuthenticationFailure(401, "UNAUTHORIZED", "Authentication is required.");
    }
}
