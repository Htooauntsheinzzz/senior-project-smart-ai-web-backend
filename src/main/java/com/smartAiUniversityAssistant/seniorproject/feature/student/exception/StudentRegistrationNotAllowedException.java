package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class StudentRegistrationNotAllowedException extends StudentFailure {
    public StudentRegistrationNotAllowedException() {
        super(403, "STUDENT_REGISTRATION_NOT_ALLOWED", "Registration is not allowed for this student account.");
    }
}
