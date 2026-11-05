package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.Pageable;

public record DocumentCategoryListQuery(int page, int size, String search, Boolean active, List<String> sort) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "search", "isActive", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "categoryCode", "categoryName", "createdAt");

    public DocumentCategoryListQuery {
        search = ListQuerySupport.search(search, 150);
        sort = ListQuerySupport.sort(sort, SORT_FIELDS);
        ListQuerySupport.pageable(page, size, sort);
    }

    public static DocumentCategoryListQuery from(HttpServletRequest request) {
        ListQuerySupport.onlyKnown(request, PARAMETERS);
        String[] sort = request.getParameterValues("sort");
        return new DocumentCategoryListQuery(ListQuerySupport.page(request, "page", 0), ListQuerySupport.page(request, "size", 20),
                request.getParameter("search"), ListQuerySupport.bool(request, "isActive"),
                sort == null ? List.of() : List.of(sort));
    }

    public Pageable pageable() { return ListQuerySupport.pageable(page, size, sort); }
}
