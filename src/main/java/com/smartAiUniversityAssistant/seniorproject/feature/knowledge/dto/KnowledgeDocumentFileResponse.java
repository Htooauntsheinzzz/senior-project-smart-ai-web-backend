package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record KnowledgeDocumentFileResponse(Long fileId, int revisionNumber, String filename, String extension,
        String mimeType, long sizeBytes, String sha256Checksum, @JsonProperty("isCurrent") boolean isCurrent,
        KnowledgeDocumentResponse.Uploader uploadedBy, Instant uploadedAt, String processingStatus) {}
