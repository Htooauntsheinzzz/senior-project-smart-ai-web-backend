package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.exception.AcademicActivityFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.data.domain.*;

public record AcademicActivityListQuery(int page, int size, Long courseSectionId, String status) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "courseSectionId", "status");

    public static AcademicActivityListQuery from(HttpServletRequest request) {
        for (var parameter : request.getParameterMap().entrySet())
            if (!PARAMETERS.contains(parameter.getKey()) || parameter.getValue().length != 1) throw invalid();
        try {
            int page = Math.toIntExact(number(request.getParameter("page"), 0));
            int size = Math.toIntExact(number(request.getParameter("size"), 20));
            String section = request.getParameter("courseSectionId");
            Long sectionId = section == null ? null : number(section, 0);
            if (size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE || sectionId != null && sectionId <= 0)
                throw invalid();
            return new AcademicActivityListQuery(page, size, sectionId, request.getParameter("status"));
        } catch (ArithmeticException e) {
            throw invalid();
        }
    }

    public Pageable pageable(String sortField) { return PageRequest.of(page, size, Sort.by(sortField, "id")); }

    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,18}")) throw invalid();
        return Long.parseLong(value);
    }
    private static AcademicActivityFailure invalid() {
        return new AcademicActivityFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
