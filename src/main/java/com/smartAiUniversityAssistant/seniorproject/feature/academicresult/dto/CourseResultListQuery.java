package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.CourseResultStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.exception.AcademicResultFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.*;

public record CourseResultListQuery(int page, int size, Long studentId, Long enrollmentId, Long semesterId,
        CourseResultStatus status) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "studentId", "enrollmentId", "semesterId", "status");

    public static CourseResultListQuery from(HttpServletRequest request) {
        for (var parameter : request.getParameterMap().entrySet())
            if (!PARAMETERS.contains(parameter.getKey()) || parameter.getValue().length != 1) throw invalid();
        try {
            int page = Math.toIntExact(number(request.getParameter("page"), 0));
            int size = Math.toIntExact(number(request.getParameter("size"), 20));
            if (size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw invalid();
            String status = request.getParameter("status");
            return new CourseResultListQuery(page, size, id(request, "studentId"), id(request, "enrollmentId"),
                    id(request, "semesterId"), status == null ? null : CourseResultStatus.valueOf(status));
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    public Pageable pageable() { return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")); }

    private static Long id(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        long parsed = number(value, 0);
        if (parsed <= 0) throw invalid();
        return parsed;
    }
    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,18}")) throw invalid();
        return Long.parseLong(value);
    }
    private static AcademicResultFailure invalid() {
        return new AcademicResultFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
