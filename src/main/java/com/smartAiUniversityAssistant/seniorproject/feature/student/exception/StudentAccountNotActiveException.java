package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentAccountNotActiveException extends StudentFailure {
    public StudentAccountNotActiveException() {
        super(403, "STUDENT_ACCOUNT_NOT_ACTIVE", "The student account is not active.");
    }
}
