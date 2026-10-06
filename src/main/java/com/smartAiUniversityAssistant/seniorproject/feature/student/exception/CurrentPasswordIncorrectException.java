package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class CurrentPasswordIncorrectException extends StudentFailure {
    public CurrentPasswordIncorrectException() {
        super(400, "CURRENT_PASSWORD_INCORRECT", "The current password is invalid.");
    }
}
