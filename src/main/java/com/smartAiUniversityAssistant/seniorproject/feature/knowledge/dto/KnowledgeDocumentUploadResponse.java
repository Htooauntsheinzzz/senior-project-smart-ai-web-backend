package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Result of an upload or file replacement. No storage path or key is ever returned. */
public record KnowledgeDocumentUploadResponse(Long documentId, Long fileId, String title, Long categoryId,
        String academicYearLabel, String filename, String extension, String mimeType, int revisionNumber,
        long sizeBytes, String sha256Checksum, String processingStatus, @JsonProperty("isActive") boolean isActive,
        Long uploadedBy, Instant uploadedAt, String message) {}
