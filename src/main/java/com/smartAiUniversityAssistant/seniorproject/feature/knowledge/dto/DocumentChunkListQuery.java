package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;

/** Strict paging of the chunk list (same rules as the page list): only page and size, size at most 100. */
public record DocumentChunkListQuery(int page, int size) {
    public static DocumentChunkListQuery from(HttpServletRequest request) {
        var query = DocumentTextPageListQuery.from(request);
        return new DocumentChunkListQuery(query.page(), query.size());
    }

    public Pageable pageable() { return new DocumentTextPageListQuery(page, size).pageable(); }
}
