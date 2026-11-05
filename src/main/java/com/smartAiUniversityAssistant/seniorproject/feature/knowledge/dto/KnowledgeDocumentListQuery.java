package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.smartAiUniversityAssistant.seniorproject.common.AcademicYearLabel;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentProcessingStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.Pageable;

/**
 * status filters the current revision's processing status. programId and departmentId match the resolved scope, so a
 * program filter also finds the documents of that program's curriculum versions.
 */
public record KnowledgeDocumentListQuery(int page, int size, String search, Long categoryId, String academicYear,
        DocumentProcessingStatus status, Boolean active, Long programId, Long departmentId, Long curriculumVersionId,
        List<String> sort) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "search", "categoryId", "academicYear",
            "status", "isActive", "programId", "departmentId", "curriculumVersionId", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "title", "academicYearLabel", "createdAt", "updatedAt");

    public KnowledgeDocumentListQuery {
        search = ListQuerySupport.search(search, 255);
        if (academicYear != null) {
            academicYear = AcademicYearLabel.normalize(academicYear);
            if (academicYear == null) throw KnowledgeFailure.invalid();
        }
        sort = ListQuerySupport.sort(sort, SORT_FIELDS);
        ListQuerySupport.pageable(page, size, sort);
    }

    public static KnowledgeDocumentListQuery from(HttpServletRequest request) {
        ListQuerySupport.onlyKnown(request, PARAMETERS);
        String status = request.getParameter("status");
        DocumentProcessingStatus parsed;
        try {
            parsed = status == null ? null : DocumentProcessingStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw KnowledgeFailure.invalid();
        }
        String[] sort = request.getParameterValues("sort");
        return new KnowledgeDocumentListQuery(ListQuerySupport.page(request, "page", 0), ListQuerySupport.page(request, "size", 20),
                request.getParameter("search"), ListQuerySupport.id(request, "categoryId"), request.getParameter("academicYear"),
                parsed, ListQuerySupport.bool(request, "isActive"), ListQuerySupport.id(request, "programId"),
                ListQuerySupport.id(request, "departmentId"), ListQuerySupport.id(request, "curriculumVersionId"),
                sort == null ? List.of() : List.of(sort));
    }

    public Pageable pageable() { return ListQuerySupport.pageable(page, size, sort); }
}
