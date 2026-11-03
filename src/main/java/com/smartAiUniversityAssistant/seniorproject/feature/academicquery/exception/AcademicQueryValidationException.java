package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception;

public class AcademicQueryValidationException extends AcademicQueryFailure {
    public AcademicQueryValidationException() { super(400, "VALIDATION_ERROR", "The request is invalid."); }
}
