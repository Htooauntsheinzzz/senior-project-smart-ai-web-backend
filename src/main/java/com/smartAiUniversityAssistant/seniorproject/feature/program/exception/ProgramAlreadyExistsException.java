package com.smartAiUniversityAssistant.seniorproject.feature.program.exception;

public class ProgramAlreadyExistsException extends ProgramFailure {
    public ProgramAlreadyExistsException() { super(409, "PROGRAM_ALREADY_EXISTS", "The program already exists in this department with the same name and degree level."); }
}
