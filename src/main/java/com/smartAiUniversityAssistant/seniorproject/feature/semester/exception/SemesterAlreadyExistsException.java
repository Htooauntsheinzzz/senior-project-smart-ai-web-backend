package com.smartAiUniversityAssistant.seniorproject.feature.semester.exception;

public class SemesterAlreadyExistsException extends SemesterFailure {
    public SemesterAlreadyExistsException() { super(409, "SEMESTER_ALREADY_EXISTS", "The semester name combination is already in use."); }
}
