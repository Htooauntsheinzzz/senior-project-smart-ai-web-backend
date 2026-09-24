package com.smartAiUniversityAssistant.seniorproject.feature.department.exception;

public class DepartmentCodeAlreadyExistsException extends DepartmentFailure {
    public DepartmentCodeAlreadyExistsException() { super(409, "DEPARTMENT_CODE_ALREADY_EXISTS", "The department code is already in use."); }
}
