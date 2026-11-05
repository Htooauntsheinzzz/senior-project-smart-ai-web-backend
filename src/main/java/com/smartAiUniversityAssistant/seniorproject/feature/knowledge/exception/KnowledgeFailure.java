package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception;

public class KnowledgeFailure extends RuntimeException {
    private final int status;
    private final String code;
    public KnowledgeFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }

    public static KnowledgeFailure invalid() {
        return new KnowledgeFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
    public static KnowledgeFailure categoryNotFound() {
        return new KnowledgeFailure(404, "DOCUMENT_CATEGORY_NOT_FOUND", "The requested document category does not exist.");
    }
    public static KnowledgeFailure categoryDuplicate() {
        return new KnowledgeFailure(409, "DOCUMENT_CATEGORY_DUPLICATE", "The category code already exists.");
    }
    public static KnowledgeFailure categoryInUse() {
        return new KnowledgeFailure(409, "DOCUMENT_CATEGORY_IN_USE",
                "The category still has knowledge documents. Move or delete them first.");
    }
    public static KnowledgeFailure categoryInvalid() {
        return new KnowledgeFailure(400, "DOCUMENT_CATEGORY_INVALID",
                "The category does not exist, is deleted, or is inactive.");
    }
    public static KnowledgeFailure documentNotFound() {
        return new KnowledgeFailure(404, "DOCUMENT_NOT_FOUND", "The requested knowledge document does not exist.");
    }
    public static KnowledgeFailure scopeInvalid() {
        return new KnowledgeFailure(400, "DOCUMENT_SCOPE_INVALID",
                "Choose at most one of curriculumVersionId, programId and departmentId, and it must exist and be active.");
    }
    public static KnowledgeFailure notEditable() {
        return new KnowledgeFailure(409, "DOCUMENT_NOT_EDITABLE", "Archived documents must be activated before they are changed.");
    }
    public static KnowledgeFailure uploadValidation() {
        return new KnowledgeFailure(400, "DOCUMENT_UPLOAD_VALIDATION_ERROR",
                "title, categoryId and a valid academicYearLabel (for example 2024-2025) are required.");
    }
    public static KnowledgeFailure fileRequired() {
        return new KnowledgeFailure(400, "DOCUMENT_FILE_REQUIRED", "A document file is required.");
    }
    public static KnowledgeFailure fileEmpty() {
        return new KnowledgeFailure(400, "DOCUMENT_FILE_EMPTY", "The document file is empty.");
    }
    public static KnowledgeFailure fileTooLarge() {
        return new KnowledgeFailure(413, "DOCUMENT_FILE_TOO_LARGE", "The document file exceeds the maximum upload size.");
    }
    public static KnowledgeFailure fileTypeUnsupported() {
        return new KnowledgeFailure(415, "DOCUMENT_FILE_TYPE_UNSUPPORTED",
                "Only PDF, DOCX, TXT and XLSX files with their matching content type are supported.");
    }
    public static KnowledgeFailure fileContentInvalid() {
        return new KnowledgeFailure(400, "DOCUMENT_FILE_CONTENT_INVALID",
                "The file content does not match its type or is not allowed.");
    }
    public static KnowledgeFailure storageFailed() {
        return new KnowledgeFailure(503, "DOCUMENT_STORAGE_FAILED", "Document storage is temporarily unavailable.");
    }
    public static KnowledgeFailure metadataSaveFailed() {
        return new KnowledgeFailure(503, "DOCUMENT_METADATA_SAVE_FAILED",
                "The document could not be saved. The uploaded file was discarded; retry the upload.");
    }
    public static KnowledgeFailure fileNotFound() {
        return new KnowledgeFailure(404, "DOCUMENT_FILE_NOT_FOUND", "The requested document file does not exist.");
    }
    public static KnowledgeFailure revisionConflict() {
        return new KnowledgeFailure(409, "DOCUMENT_REVISION_CONFLICT",
                "Another file revision was saved at the same time. Retry the request.");
    }
    public static KnowledgeFailure processingNotAvailable() {
        return new KnowledgeFailure(503, "DOCUMENT_PROCESSING_NOT_AVAILABLE",
                "Document ingestion is not available yet. The document stays UPLOADED until the ingestion worker exists.");
    }
    public static KnowledgeFailure uploadDisabled() {
        return new KnowledgeFailure(503, "DOCUMENT_UPLOAD_DISABLED", "Knowledge document upload is disabled.");
    }
}
