package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class PasswordConfirmationMismatchException extends StudentFailure {
    public PasswordConfirmationMismatchException() {
        super(400, "PASSWORD_CONFIRMATION_MISMATCH", "The password confirmation does not match the password.");
    }
}
