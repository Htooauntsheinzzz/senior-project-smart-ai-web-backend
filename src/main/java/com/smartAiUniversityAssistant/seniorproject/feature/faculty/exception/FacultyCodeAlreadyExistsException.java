package com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception;

public class FacultyCodeAlreadyExistsException extends RuntimeException {
    public FacultyCodeAlreadyExistsException() { super("The faculty code is already in use."); }
}
