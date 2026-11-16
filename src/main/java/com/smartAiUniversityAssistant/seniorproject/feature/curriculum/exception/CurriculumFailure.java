package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.exception;

public class CurriculumFailure extends RuntimeException {
    private final int status;
    private final String code;
    public CurriculumFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }

    public static CurriculumFailure invalid() {
        return new CurriculumFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
    public static CurriculumFailure notFound() {
        return new CurriculumFailure(404, "CURRICULUM_NOT_FOUND", "The requested curriculum version does not exist.");
    }
    public static CurriculumFailure duplicate() {
        return new CurriculumFailure(409, "CURRICULUM_VERSION_DUPLICATE",
                "The version code already exists for this program.");
    }
    public static CurriculumFailure alreadyActive() {
        return new CurriculumFailure(409, "CURRICULUM_ALREADY_ACTIVE", "The curriculum version is already active.");
    }
    public static CurriculumFailure invalidProgram() {
        return new CurriculumFailure(400, "CURRICULUM_INVALID_PROGRAM",
                "The program does not exist, is deleted, or is inactive.");
    }
    public static CurriculumFailure invalidTransition() {
        return new CurriculumFailure(409, "CURRICULUM_INVALID_STATUS_TRANSITION",
                "The curriculum version cannot change to the requested status.");
    }
    public static CurriculumFailure hasDocuments() {
        return new CurriculumFailure(409, "CURRICULUM_HAS_DOCUMENTS",
                "The curriculum version still has knowledge documents. Delete or move them first.");
    }
}
