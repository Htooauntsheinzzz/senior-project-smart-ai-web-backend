package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeAudience;

/**
 * One passage of a search result. Never a vector, a distance, a storage key, a checksum or a file name. Step 15B:
 * audience is whom the passage applies to, seen from studentScope (null without it); neighbor is true for the
 * continuation of a hit's section, which has the rank and score of its hit.
 */
public record KnowledgeSearchResultResponse(int rank, double score, long documentId, String title, Category category,
        String academicYearLabel, String scopeType, long fileId, int revisionNumber, int chunkIndex, String sectionTitle,
        String unitType, int pageStart, int pageEnd, String content, KnowledgeAudience audience, boolean neighbor) {

    public record Category(Long id, String code, String name) {}
}
