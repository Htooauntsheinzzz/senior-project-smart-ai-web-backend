package com.smartAiUniversityAssistant.seniorproject.feature.department.exception;

public class DepartmentNameAlreadyExistsException extends DepartmentFailure {
    public DepartmentNameAlreadyExistsException() { super(409, "DEPARTMENT_NAME_ALREADY_EXISTS", "The department name is already in use in this faculty."); }
}
