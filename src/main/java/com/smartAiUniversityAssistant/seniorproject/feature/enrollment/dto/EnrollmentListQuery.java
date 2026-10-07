package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.exception.EnrollmentFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.data.domain.*;

public record EnrollmentListQuery(int page, int size, String search, Long studentId, Long courseId,
        Long courseSectionId, Long semesterId, EnrollmentStatus status, LocalDate enrollmentDate, List<String> sort) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "search", "studentId", "courseId",
            "courseSectionId", "semesterId", "status", "enrollmentDate", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "enrollmentDate", "status", "createdAt", "updatedAt");

    public EnrollmentListQuery {
        if (page < 0 || size < 1 || size > 100 || !positiveOrNull(studentId) || !positiveOrNull(courseId)
                || !positiveOrNull(courseSectionId) || !positiveOrNull(semesterId)) throw invalid();
        search = search == null || search.isBlank() ? null : search.trim();
        if (search != null && search.length() > 255) throw invalid();
        List<String> entries = sort == null || sort.isEmpty() ? List.of("enrollmentDate,desc") : sort;
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

    public static EnrollmentListQuery from(HttpServletRequest request) {
        for (var parameter : request.getParameterMap().entrySet()) {
            if (!PARAMETERS.contains(parameter.getKey())
                    || !parameter.getKey().equals("sort") && parameter.getValue().length != 1) throw invalid();
        }
        try {
            long page = number(request.getParameter("page"), 0);
            long size = number(request.getParameter("size"), 20);
            String status = request.getParameter("status");
            // The filter requires the exact stored (uppercase) value.
            if (status != null && Arrays.stream(EnrollmentStatus.values()).noneMatch(s -> s.name().equals(status)))
                throw invalid();
            String date = request.getParameter("enrollmentDate");
            String[] sort = request.getParameterValues("sort");
            return new EnrollmentListQuery(Math.toIntExact(page), Math.toIntExact(size), request.getParameter("search"),
                    optionalId(request, "studentId"), optionalId(request, "courseId"),
                    optionalId(request, "courseSectionId"), optionalId(request, "semesterId"),
                    status == null ? null : EnrollmentStatus.valueOf(status), date == null ? null : LocalDate.parse(date),
                    sort == null ? List.of() : List.of(sort));
        } catch (ArithmeticException | NumberFormatException | DateTimeParseException e) {
            throw invalid();
        }
    }

    public Pageable pageable() {
        return PageRequest.of(page, size, Sort.by(sort.stream().map(entry -> {
            String[] pair = entry.split(",");
            return new Sort.Order(pair[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, pair[0]);
        }).toList()));
    }

    private static Long optionalId(HttpServletRequest request, String name) {
        return request.getParameter(name) == null ? null : number(request.getParameter(name), 0);
    }
    private static boolean positiveOrNull(Long value) { return value == null || value > 0; }
    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw invalid();
        return Long.parseLong(value);
    }
    private static EnrollmentFailure invalid() {
        return new EnrollmentFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
