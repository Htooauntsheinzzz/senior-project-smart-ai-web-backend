package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentAuthSessionUnavailableException extends StudentFailure {
    public StudentAuthSessionUnavailableException() {
        super(503, "AUTH_SESSION_UNAVAILABLE", "Student sign-in is temporarily unavailable. Try again later.");
    }
}
