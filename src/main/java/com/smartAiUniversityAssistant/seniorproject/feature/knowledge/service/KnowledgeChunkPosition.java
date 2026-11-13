package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/** Step 15B: one chunk position in a file revision, used to read neighbouring chunks (spec file 54, section 8.2). */
public record KnowledgeChunkPosition(long fileId, int chunkIndex) {}
