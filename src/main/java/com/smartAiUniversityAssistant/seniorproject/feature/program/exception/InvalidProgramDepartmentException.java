package com.smartAiUniversityAssistant.seniorproject.feature.program.exception;

public class InvalidProgramDepartmentException extends ProgramFailure {
    public InvalidProgramDepartmentException() { super(400, "INVALID_PROGRAM_DEPARTMENT", "The selected department is inactive or does not belong to the selected faculty."); }
}
