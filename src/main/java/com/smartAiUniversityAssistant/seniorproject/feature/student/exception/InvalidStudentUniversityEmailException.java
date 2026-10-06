package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class InvalidStudentUniversityEmailException extends StudentFailure {
    public InvalidStudentUniversityEmailException() {
        super(400, "INVALID_STUDENT_UNIVERSITY_EMAIL",
                "Registration requires a valid university email ending in @rsu.ac.th.");
    }
}
