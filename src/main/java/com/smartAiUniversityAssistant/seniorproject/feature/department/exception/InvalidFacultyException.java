package com.smartAiUniversityAssistant.seniorproject.feature.department.exception;

public class InvalidFacultyException extends DepartmentFailure {
    public InvalidFacultyException() { super(400, "INVALID_FACULTY", "The selected faculty is inactive."); }
}
