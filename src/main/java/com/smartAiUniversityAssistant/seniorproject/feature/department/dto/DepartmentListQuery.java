package com.smartAiUniversityAssistant.seniorproject.feature.department.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.department.exception.DepartmentFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.*;

public record DepartmentListQuery(int page, int size, String search, Long facultyId, Boolean active,
        List<String> sort) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "search", "facultyId", "status", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "departmentCode", "departmentName", "createdAt");

    public DepartmentListQuery {
        if (page < 0 || size < 1 || size > 100 || facultyId != null && facultyId <= 0) throw invalid();
        search = search == null || search.isBlank() ? null : search.trim();
        if (search != null && search.length() > 255) throw invalid();
        List<String> entries = sort == null || sort.isEmpty() ? List.of("createdAt,desc") : sort;
        if (entries.size() > 3) throw invalid();
        var fields = new HashSet<String>();
        var effective = new ArrayList<String>();
        for (String entry : entries) {
            String[] parts = entry == null ? new String[0] : entry.split(",", -1);
            if (parts.length != 2 || !SORT_FIELDS.contains(parts[0])
                    || !Set.of("asc", "desc").contains(parts[1]) || !fields.add(parts[0])) throw invalid();
            effective.add(entry);
        }
        if (!fields.contains("id")) effective.add("id,asc");
        sort = List.copyOf(effective);
        if ((long) page * size > Integer.MAX_VALUE) throw invalid();
    }

    public static DepartmentListQuery from(HttpServletRequest request) {
        for (var parameter : request.getParameterMap().entrySet()) {
            if (!PARAMETERS.contains(parameter.getKey())
                    || !parameter.getKey().equals("sort") && parameter.getValue().length != 1) throw invalid();
        }
        try {
            long page = number(request.getParameter("page"), 0);
            long size = number(request.getParameter("size"), 20);
            Long faculty = request.getParameter("facultyId") == null ? null : number(request.getParameter("facultyId"), 0);
            String status = request.getParameter("status");
            if (status != null && !Set.of("ACTIVE", "INACTIVE").contains(status)) throw invalid();
            String[] sort = request.getParameterValues("sort");
            return new DepartmentListQuery(Math.toIntExact(page), Math.toIntExact(size), request.getParameter("search"),
                    faculty, status == null ? null : status.equals("ACTIVE"), sort == null ? List.of() : List.of(sort));
        } catch (ArithmeticException | NumberFormatException e) {
            throw invalid();
        }
    }

    public Pageable pageable() {
        return PageRequest.of(page, size, Sort.by(sort.stream().map(entry -> {
            String[] pair = entry.split(",");
            return new Sort.Order(pair[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, pair[0]);
        }).toList()));
    }

    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw invalid();
        return Long.parseLong(value);
    }
    private static DepartmentFailure invalid() {
        return new DepartmentFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
