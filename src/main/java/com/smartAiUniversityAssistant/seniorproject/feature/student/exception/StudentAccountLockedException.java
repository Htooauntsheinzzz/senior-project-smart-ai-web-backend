package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentAccountLockedException extends StudentFailure {
    public StudentAccountLockedException() {
        super(423, "STUDENT_ACCOUNT_LOCKED", "The student account is temporarily locked. Try again later.");
    }
}
