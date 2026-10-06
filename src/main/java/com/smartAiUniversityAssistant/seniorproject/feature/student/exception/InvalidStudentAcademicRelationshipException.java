package com.smartAiUniversityAssistant.seniorproject.feature.student.exception;

public class InvalidStudentAcademicRelationshipException extends StudentFailure {
    public InvalidStudentAcademicRelationshipException(String message) {
        super(400, "INVALID_STUDENT_ACADEMIC_RELATIONSHIP", message);
    }
}
