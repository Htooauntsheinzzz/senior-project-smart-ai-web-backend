package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

/** Counts of active, non-deleted documents by their current revision's processing status. */
public record KnowledgeDocumentStatisticsResponse(long totalDocuments, long readyDocuments, long processingDocuments,
        long failedDocuments) {}
