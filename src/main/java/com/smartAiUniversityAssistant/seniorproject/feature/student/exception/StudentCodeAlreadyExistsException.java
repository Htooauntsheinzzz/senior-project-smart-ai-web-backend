package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentCodeAlreadyExistsException extends StudentFailure {
    public StudentCodeAlreadyExistsException() { super(409, "STUDENT_CODE_ALREADY_EXISTS", "The student code is already in use."); }
}
