package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.util.List;

/** query is the prepared question that was embedded; the other values are the ones used, after defaults. */
public record KnowledgeSearchResponse(String query, String model, int topK, double minScore,
        KnowledgeSearchRequest.StudentScope studentScope, int ownProgramQuota, int maxNeighborPassages, long tookMs,
        List<KnowledgeSearchResultResponse> results) {}
