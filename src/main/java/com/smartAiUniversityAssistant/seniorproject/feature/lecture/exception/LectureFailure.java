package com.smartAiUniversityAssistant.seniorproject.feature.lecture.exception;

public class LectureFailure extends RuntimeException {
    private final int status;
    private final String code;
    public LectureFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
}
