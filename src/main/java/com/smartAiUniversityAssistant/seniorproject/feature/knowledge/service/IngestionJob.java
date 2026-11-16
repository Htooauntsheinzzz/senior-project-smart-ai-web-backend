package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;

/** The file revision being processed. Created by the worker when it claims a job; never built from request data. */
public record IngestionJob(long processingId, long documentId, long fileId, int revisionNumber, String storageKey,
        String fileExtension, String mimeType, long sizeBytes, String sha256Checksum, int attempt,
        IngestionStageType firstStage) {}
