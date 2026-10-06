package com.smartAiUniversityAssistant.seniorproject.feature.department.exception;

public class DepartmentInUseException extends DepartmentFailure {
    public DepartmentInUseException() { super(409, "DEPARTMENT_IN_USE", "Cannot delete department because users are assigned to it."); }
}
