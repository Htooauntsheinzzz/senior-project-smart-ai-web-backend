package com.smartAiUniversityAssistant.seniorproject.feature.course.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.course.exception.CourseFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.*;

public record CourseListQuery(int page, int size, String search, Long facultyId, Long departmentId, Long programId,
        Long semesterId, Integer recommendedAcademicYear, Integer courseType, String status, List<String> sort) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "search", "facultyId", "departmentId",
            "programId", "semesterId", "recommendedAcademicYear", "courseType", "status", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "courseCode", "courseName", "creditHours",
            "recommendedAcademicYear", "courseType", "maximumStudentsPerSection", "createdAt", "updatedAt");
    private static final Set<Integer> COURSE_TYPES = Set.of(100, 200, 300);

    public CourseListQuery {
        if (page < 0 || size < 1 || size > 100 || facultyId != null && facultyId <= 0
                || departmentId != null && departmentId <= 0 || programId != null && programId <= 0
                || semesterId != null && semesterId <= 0
                || recommendedAcademicYear != null && recommendedAcademicYear <= 0) throw invalid();
        if (courseType != null && !COURSE_TYPES.contains(courseType)) throw invalid();
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

    public static CourseListQuery from(HttpServletRequest request) {
        for (var parameter : request.getParameterMap().entrySet()) {
            if (!PARAMETERS.contains(parameter.getKey())
                    || !parameter.getKey().equals("sort") && parameter.getValue().length != 1) throw invalid();
        }
        try {
            long page = number(request.getParameter("page"), 0);
            long size = number(request.getParameter("size"), 20);
            Long faculty = optionalId(request.getParameter("facultyId"));
            Long department = optionalId(request.getParameter("departmentId"));
            Long program = optionalId(request.getParameter("programId"));
            Long semester = optionalId(request.getParameter("semesterId"));
            Long year = optionalId(request.getParameter("recommendedAcademicYear"));
            Long type = optionalId(request.getParameter("courseType"));
            String status = request.getParameter("status");
            if (status != null && !Set.of("ACTIVE", "INACTIVE").contains(status)) throw invalid();
            String[] sort = request.getParameterValues("sort");
            return new CourseListQuery(Math.toIntExact(page), Math.toIntExact(size), request.getParameter("search"),
                    faculty, department, program, semester,
                    year == null ? null : Math.toIntExact(year), type == null ? null : Math.toIntExact(type),
                    status, sort == null ? List.of() : List.of(sort));
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

    private static Long optionalId(String value) { return value == null ? null : number(value, 0); }
    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw invalid();
        return Long.parseLong(value);
    }
    private static CourseFailure invalid() {
        return new CourseFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
