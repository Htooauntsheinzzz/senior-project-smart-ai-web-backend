package com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception;

public class FacultyNameAlreadyExistsException extends RuntimeException {
    public FacultyNameAlreadyExistsException() { super("The faculty English name is already in use."); }
}
