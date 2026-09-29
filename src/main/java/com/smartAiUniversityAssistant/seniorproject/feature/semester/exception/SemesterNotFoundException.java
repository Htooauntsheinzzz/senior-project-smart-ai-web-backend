package com.smartAiUniversityAssistant.seniorproject.feature.semester.exception;

public class SemesterNotFoundException extends SemesterFailure {
    public SemesterNotFoundException() { super(404, "SEMESTER_NOT_FOUND", "The requested semester does not exist."); }
}
