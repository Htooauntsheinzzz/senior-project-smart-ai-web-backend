package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

// PENDING students have no credential yet, so credential operations do not apply to them.
public class StudentNotRegisteredException extends StudentFailure {
    public StudentNotRegisteredException() {
        super(409, "STUDENT_NOT_REGISTERED", "The student has not completed mobile registration.");
    }
}
