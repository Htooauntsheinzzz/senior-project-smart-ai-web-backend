package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class InvalidStudentCredentialsException extends StudentFailure {
    public InvalidStudentCredentialsException() {
        super(401, "INVALID_STUDENT_CREDENTIALS", "Unable to authenticate with the supplied credentials.");
    }
}
