package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.*;

/** Strict paging of the page and chunk inspection lists: only page and size, size at most 100, fixed order. */
public record DocumentTextPageListQuery(int page, int size) {
    private static final Set<String> PARAMETERS = Set.of("page", "size");

    public DocumentTextPageListQuery {
        ListQuerySupport.pageable(page, size, List.of("id,asc"));
    }

    public static DocumentTextPageListQuery from(HttpServletRequest request) {
        ListQuerySupport.onlyKnown(request, PARAMETERS);
        return new DocumentTextPageListQuery(ListQuerySupport.page(request, "page", 0), ListQuerySupport.page(request, "size", 20));
    }

    /** Unsorted: the statements order by page number or chunk index themselves. */
    public Pageable pageable() { return PageRequest.of(page, size); }
}
