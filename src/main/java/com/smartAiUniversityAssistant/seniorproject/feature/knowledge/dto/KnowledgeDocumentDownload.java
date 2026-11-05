package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.io.InputStream;

/** An opened stored revision; the controller streams and closes it. */
public record KnowledgeDocumentDownload(String filename, String mimeType, long sizeBytes, InputStream content) {}
