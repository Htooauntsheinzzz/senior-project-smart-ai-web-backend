package com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception;

public class FacultyNotFoundException extends RuntimeException {
    public FacultyNotFoundException() { super("The requested faculty does not exist."); }
}
