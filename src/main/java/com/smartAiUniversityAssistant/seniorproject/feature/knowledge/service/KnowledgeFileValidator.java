package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import org.springframework.web.multipart.MultipartFile;

/** Checks an upload's name, declared media type and actual content together before anything is stored. */
public interface KnowledgeFileValidator {
    ValidatedFile validate(MultipartFile file);

    /** filename is sanitized for display only; mimeType is the canonical type for the extension. */
    record ValidatedFile(String filename, String extension, String mimeType) {}
}
