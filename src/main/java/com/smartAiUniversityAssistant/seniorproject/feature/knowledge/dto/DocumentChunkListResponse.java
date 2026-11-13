package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.util.List;

public record DocumentChunkListResponse(long documentId, long fileId, int revisionNumber, List<DocumentChunkSummaryResponse> content,
        int page, int size, long totalElements, int totalPages, boolean first, boolean last) {}
