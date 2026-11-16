package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/** One chunk before it is stored. contentHash is the SHA-256 of KnowledgeTextChunker.embeddingInput(sectionTitle, content). */
public record ChunkDraft(String content, String sectionTitle, int pageStart, int pageEnd, int charCount, int tokenEstimate,
        String contentHash) {}
