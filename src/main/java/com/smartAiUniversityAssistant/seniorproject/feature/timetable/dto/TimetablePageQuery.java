package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.timetable.exception.TimetableFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.domain.*;

/** Strict paging, sorting and filter parameters for the admin lists: an unknown parameter or sort field is a 400. */
public final class TimetablePageQuery {
    private final HttpServletRequest request;
    private final int page, size;
    private final List<String> sort;
    private final Map<String, String> sortPaths;
    private final String search;

    private TimetablePageQuery(HttpServletRequest request, int page, int size, List<String> sort, Map<String, String> sortPaths, String search) {
        this.request = request;
        this.page = page;
        this.size = size;
        this.sort = sort;
        this.sortPaths = sortPaths;
        this.search = search;
    }

    /** sortPaths maps the public sort names to entity property paths; defaultSort is "name,direction". */
    public static TimetablePageQuery from(HttpServletRequest request, Set<String> filters, Map<String, String> sortPaths, String defaultSort) {
        for (var parameter : request.getParameterMap().entrySet()) {
            String key = parameter.getKey();
            boolean known = key.equals("page") || key.equals("size") || key.equals("sort") || key.equals("search") || filters.contains(key);
            if (!known || !key.equals("sort") && parameter.getValue().length != 1) throw TimetableFailure.invalid("Unknown or repeated parameter: " + key);
        }
        long page = number(request.getParameter("page"), 0);
        long size = number(request.getParameter("size"), 20);
        if (size < 1 || size > 100 || page * size > Integer.MAX_VALUE) throw TimetableFailure.invalid("page or size is out of range.");
        String[] requested = request.getParameterValues("sort");
        List<String> entries = requested == null ? List.of(defaultSort) : List.of(requested);
        if (entries.size() > 3) throw TimetableFailure.invalid("At most three sort entries are allowed.");
        var seen = new HashSet<String>();
        var effective = new ArrayList<String>();
        for (String entry : entries) {
            String[] parts = entry.split(",", -1);
            if (parts.length != 2 || !sortPaths.containsKey(parts[0]) || !Set.of("asc", "desc").contains(parts[1]) || !seen.add(parts[0]))
                throw TimetableFailure.invalid("Unsupported sort: " + entry);
            effective.add(entry);
        }
        if (!seen.contains("id")) effective.add("id,asc");
        String search = request.getParameter("search");
        search = search == null || search.isBlank() ? null : search.trim();
        if (search != null && search.length() > 200) throw TimetableFailure.invalid("search is too long.");
        return new TimetablePageQuery(request, (int) page, (int) size, List.copyOf(effective), sortPaths, search);
    }

    public Pageable pageable() {
        return PageRequest.of(page, size, Sort.by(sort.stream().map(entry -> {
            String[] pair = entry.split(",");
            return new Sort.Order(pair[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortPaths.getOrDefault(pair[0], pair[0]));
        }).toList()));
    }
    public String search() { return search; }
    public Long id(String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        long parsed = number(value, 0);
        if (parsed <= 0) throw TimetableFailure.invalid(name + " must be a positive id.");
        return parsed;
    }
    public Integer integer(String name, int min, int max) {
        String value = request.getParameter(name);
        if (value == null) return null;
        if (!value.matches("-?[0-9]{1,5}") || Integer.parseInt(value) < min || Integer.parseInt(value) > max)
            throw TimetableFailure.invalid(name + " is out of range.");
        return Integer.valueOf(value);
    }
    public LocalDate date(String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw TimetableFailure.invalid(name + " must be an ISO date (yyyy-MM-dd).");
        }
    }
    public String text(String name) {
        String value = request.getParameter(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
    public <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        String value = request.getParameter(name);
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw TimetableFailure.invalid(name + " has an unsupported value.");
        }
    }
    public <S, T> TimetablePageResponse<T> response(Page<S> result, Function<S, T> mapper) {
        return new TimetablePageResponse<>(result.getContent().stream().map(mapper).toList(), page, size, result.getTotalElements(),
                result.getTotalPages(), page == 0, page >= result.getTotalPages() - 1, sort);
    }
    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,12}")) throw TimetableFailure.invalid("A numeric parameter is malformed.");
        return Long.parseLong(value);
    }
}
