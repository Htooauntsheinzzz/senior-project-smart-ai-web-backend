package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentNotFoundException extends StudentFailure {
    public StudentNotFoundException() { super(404, "STUDENT_NOT_FOUND", "The requested student does not exist."); }
}
