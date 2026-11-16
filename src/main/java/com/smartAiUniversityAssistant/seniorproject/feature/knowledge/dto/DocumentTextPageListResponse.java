package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.util.List;

/** The extracted units of the current revision, in page-number order. */
public record DocumentTextPageListResponse(long documentId, long fileId, int revisionNumber,
        List<DocumentTextPageSummaryResponse> content, int page, int size, long totalElements, int totalPages,
        boolean first, boolean last) {}
