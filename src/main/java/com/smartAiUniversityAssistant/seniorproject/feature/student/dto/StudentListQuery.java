package com.smartAiUniversityAssistant.seniorproject.feature.student.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.student.exception.StudentFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.*;

public record StudentListQuery(int page, int size, String search, Long facultyId, Long departmentId, Long programId,
        Long semesterId, Integer academicYear, Integer enrollmentYear, String status, List<String> sort) {
    private static final Set<String> PARAMETERS = Set.of("page", "size", "search", "facultyId", "departmentId",
            "programId", "semesterId", "academicYear", "enrollmentYear", "status", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "studentCode", "universityEmail", "firstName",
            "lastName", "academicYear", "enrollmentYear", "accountStatus", "createdAt", "updatedAt");
    private static final Set<String> STATUSES = Set.of("PENDING", "ACTIVE", "INACTIVE", "SUSPENDED");

    public StudentListQuery {
        if (page < 0 || size < 1 || size > 100 || facultyId != null && facultyId <= 0
                || departmentId != null && departmentId <= 0 || programId != null && programId <= 0
                || semesterId != null && semesterId <= 0
                || academicYear != null && (academicYear < 1900 || academicYear > 9999)
                || enrollmentYear != null && (enrollmentYear < 1900 || enrollmentYear > 9999)) throw invalid();
        if (status != null && !STATUSES.contains(status)) throw invalid();
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

    public static StudentListQuery from(HttpServletRequest request) {
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
            Long academicYear = optionalId(request.getParameter("academicYear"));
            Long enrollmentYear = optionalId(request.getParameter("enrollmentYear"));
            String[] sort = request.getParameterValues("sort");
            return new StudentListQuery(Math.toIntExact(page), Math.toIntExact(size), request.getParameter("search"),
                    faculty, department, program, semester,
                    academicYear == null ? null : Math.toIntExact(academicYear),
                    enrollmentYear == null ? null : Math.toIntExact(enrollmentYear),
                    request.getParameter("status"), sort == null ? List.of() : List.of(sort));
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
    private static StudentFailure invalid() {
        return new StudentFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
}
