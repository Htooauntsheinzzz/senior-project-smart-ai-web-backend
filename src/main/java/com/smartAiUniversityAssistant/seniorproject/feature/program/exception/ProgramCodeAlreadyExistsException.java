package com.smartAiUniversityAssistant.seniorproject.feature.program.exception;

public class ProgramCodeAlreadyExistsException extends ProgramFailure {
    public ProgramCodeAlreadyExistsException() { super(409, "PROGRAM_CODE_ALREADY_EXISTS", "The program code is already in use."); }
}
