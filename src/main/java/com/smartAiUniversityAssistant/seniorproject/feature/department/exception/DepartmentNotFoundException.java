package com.smartAiUniversityAssistant.seniorproject.feature.department.exception;

public class DepartmentNotFoundException extends DepartmentFailure {
    public DepartmentNotFoundException() { super(404, "DEPARTMENT_NOT_FOUND", "The requested department does not exist."); }
}
