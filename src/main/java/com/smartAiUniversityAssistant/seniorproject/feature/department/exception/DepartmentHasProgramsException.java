package com.smartAiUniversityAssistant.seniorproject.feature.department.exception;

public class DepartmentHasProgramsException extends DepartmentFailure {
    public DepartmentHasProgramsException() {
        super(409, "DEPARTMENT_HAS_PROGRAMS", "Cannot delete department because programs are assigned to it.");
    }
}
