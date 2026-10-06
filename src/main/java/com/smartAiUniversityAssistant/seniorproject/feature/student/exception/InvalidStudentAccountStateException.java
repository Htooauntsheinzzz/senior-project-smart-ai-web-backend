package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class InvalidStudentAccountStateException extends StudentFailure {
    public InvalidStudentAccountStateException() {
        super(409, "INVALID_STUDENT_ACCOUNT_STATE",
                "Student account configuration is inconsistent. Please contact the administrator.");
    }
}
