package com.smartAiUniversityAssistant.seniorproject.feature.faculty.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.faculty.exception.FacultyQueryValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

public record FacultyListQuery(String page, String size, String search, String status,
        List<String> sort, Set<String> unknownParameters) {
    private static final Set<String> ALLOWED = Set.of("page", "size", "search", "status", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "facultyCode", "facultyNameEn",
            "facultyNameTh", "isActive", "createdAt", "updatedAt");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    public static FacultyListQuery from(HttpServletRequest request) {
        Set<String> unknown = new LinkedHashSet<>();
        request.getParameterMap().keySet().forEach(name -> {
            if (!ALLOWED.contains(name)) unknown.add(name);
        });
        String[] sortValues = request.getParameterValues("sort");
        return new FacultyListQuery(request.getParameter("page"), request.getParameter("size"),
                request.getParameter("search"), request.getParameter("status"),
                sortValues == null ? List.of() : List.of(sortValues), unknown);
    }

    public Parsed parse() {
        if (!unknownParameters.isEmpty()) throw invalid("Unknown query parameter.");
        int parsedPage = parsePage();
        int parsedSize = parseSize();
        String parsedSearch = search == null ? null : search.trim();
        if (parsedSearch != null && parsedSearch.isEmpty()) parsedSearch = null;
        if (parsedSearch != null && parsedSearch.length() > 255) throw invalid("search is too long.");
        Boolean active = null;
        if (status != null) {
            if (!STATUSES.contains(status)) throw invalid("status is invalid.");
            active = status.equals("ACTIVE");
        }
        return new Parsed(parsedPage, parsedSize, parsedSearch, active, parseSort());
    }

    private int parsePage() {
        if (page == null) return 0;
        if (!page.matches("\\d+")) throw invalid("page must be a non-negative integer.");
        try {
            return Integer.parseInt(page);
        } catch (NumberFormatException e) {
            throw invalid("page is out of range.");
        }
    }

    private int parseSize() {
        if (size == null) return 20;
        if (!size.matches("\\d+")) throw invalid("size must be a non-negative integer.");
        try {
            int parsed = Integer.parseInt(size);
            if (parsed < 1 || parsed > 100) throw invalid("size must be between 1 and 100.");
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid("size is out of range.");
        }
    }

    private List<Parsed.Order> parseSort() {
        List<Parsed.Order> orders = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> entries = sort.isEmpty() ? List.of("createdAt,desc") : sort;
        if (entries.size() > 3) throw invalid("At most three sort entries are allowed.");
        for (String entry : entries) {
            int comma = entry == null ? -1 : entry.indexOf(',');
            if (comma <= 0 || comma != entry.lastIndexOf(',') || comma == entry.length() - 1)
                throw invalid("sort entries must use the field,direction format.");
            String field = entry.substring(0, comma);
            String direction = entry.substring(comma + 1);
            if (!SORT_FIELDS.contains(field) || !(direction.equals("asc") || direction.equals("desc"))
                    || !seen.add(field)) throw invalid("sort entry is unsupported.");
            orders.add(new Parsed.Order(field, direction.equals("asc")));
        }
        if (!seen.contains("id")) orders.add(new Parsed.Order("id", true));
        return List.copyOf(orders);
    }

    private FacultyQueryValidationException invalid(String message) {
        return new FacultyQueryValidationException(message);
    }

    public record Parsed(int page, int size, String search, Boolean active, List<Order> sort) {
        public Parsed {
            sort = sort == null ? List.of() : List.copyOf(sort);
        }
        public record Order(String field, boolean ascending) {}
    }
}
