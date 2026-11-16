package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.time.Instant;
import java.util.List;

/**
 * Answer of the Admin chat and of the knowledge test (spec file 59). passages are the document passages the model was
 * given, in rank order, never null. grounded is false when no passage matched: the knowledge test then returns a fixed
 * message without calling the model (model is null and generationMs 0).
 */
public record AdminAssistantAnswerResponse(String message, String requestId, String model, Instant createdAt, boolean grounded,
        long retrievalMs, long generationMs, List<Passage> passages) {

    public AdminAssistantAnswerResponse {
        passages = passages == null ? List.of() : List.copyOf(passages);
    }

    /** One retrieved passage. score is the cosine similarity (0 to 1) of the passage and the question. */
    public record Passage(int rank, double score, long documentId, String title, String category, String academicYear,
            String sectionTitle, String unitType, int pageStart, int pageEnd, String content) {}
}
