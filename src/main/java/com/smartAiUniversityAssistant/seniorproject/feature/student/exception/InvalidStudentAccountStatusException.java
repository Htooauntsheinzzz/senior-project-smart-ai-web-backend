package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class InvalidStudentAccountStatusException extends StudentFailure {
    public InvalidStudentAccountStatusException() {
        super(400, "INVALID_STUDENT_ACCOUNT_STATUS", "The requested student account status is not allowed.");
    }
}
