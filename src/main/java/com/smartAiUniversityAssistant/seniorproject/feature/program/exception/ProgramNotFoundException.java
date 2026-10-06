package com.smartAiUniversityAssistant.seniorproject.feature.program.exception;

public class ProgramNotFoundException extends ProgramFailure {
    public ProgramNotFoundException() { super(404, "PROGRAM_NOT_FOUND", "The requested program does not exist."); }
}
