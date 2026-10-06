package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentAlreadyRegisteredException extends StudentFailure {
    public StudentAlreadyRegisteredException() {
        super(409, "STUDENT_ALREADY_REGISTERED", "Student account is already registered. Please sign in.");
    }
}
