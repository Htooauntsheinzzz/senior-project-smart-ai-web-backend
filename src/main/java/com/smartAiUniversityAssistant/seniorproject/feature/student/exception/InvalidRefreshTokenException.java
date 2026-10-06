package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class InvalidRefreshTokenException extends StudentFailure {
    public InvalidRefreshTokenException() {
        super(401, "INVALID_REFRESH_TOKEN", "Unable to refresh this session.");
    }
}
