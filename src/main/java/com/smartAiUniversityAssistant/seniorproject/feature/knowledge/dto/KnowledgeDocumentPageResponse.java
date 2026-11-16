package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import java.util.List;

public record KnowledgeDocumentPageResponse(List<KnowledgeDocumentResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
