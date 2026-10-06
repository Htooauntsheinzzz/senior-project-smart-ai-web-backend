package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentEmailAlreadyExistsException extends StudentFailure {
    public StudentEmailAlreadyExistsException() { super(409, "STUDENT_EMAIL_ALREADY_EXISTS", "The university email is already in use."); }
}
