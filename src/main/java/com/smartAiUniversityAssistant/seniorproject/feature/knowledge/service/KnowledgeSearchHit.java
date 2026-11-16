package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

/**
 * One passage of a Student search, without ids of the chunk or file. Step 15B: audience is whom the passage applies to
 * (never null for a Student search); neighbor is true for the continuation of a hit's section, which carries the rank
 * and score of its hit.
 */
public record KnowledgeSearchHit(int rank, double score, long documentId, String title, String categoryName,
        String academicYearLabel, String sectionTitle, String unitType, int pageStart, int pageEnd, String content,
        KnowledgeAudience audience, boolean neighbor) {

    /** Spec file 52 call sites and tests: no audience, not a neighbour. */
    public KnowledgeSearchHit(int rank, double score, long documentId, String title, String categoryName,
            String academicYearLabel, String sectionTitle, String unitType, int pageStart, int pageEnd, String content) {
        this(rank, score, documentId, title, categoryName, academicYearLabel, sectionTitle, unitType, pageStart, pageEnd,
                content, null, false);
    }
}
