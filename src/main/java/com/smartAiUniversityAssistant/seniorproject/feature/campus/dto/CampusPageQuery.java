package com.smartAiUniversityAssistant.seniorproject.feature.campus.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.campus.exception.CampusFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.domain.*;

/** Strict paging, sorting and filter parameters for the admin lists: an unknown parameter or sort field is a 400. */
public final class CampusPageQuery {
    private final HttpServletRequest request;
    private final int page, size;
    private final List<String> sort;
    private final String search;

    private CampusPageQuery(HttpServletRequest request, int page, int size, List<String> sort, String search) {
        this.request = request;
        this.page = page;
        this.size = size;
        this.sort = sort;
        this.search = search;
    }

    public static CampusPageQuery from(HttpServletRequest request, Set<String> filters, Set<String> sortFields, String defaultSort) {
        for (var parameter : request.getParameterMap().entrySet()) {
            String key = parameter.getKey();
            boolean known = key.equals("page") || key.equals("size") || key.equals("sort") || key.equals("search") || filters.contains(key);
            if (!known || !key.equals("sort") && parameter.getValue().length != 1) throw CampusFailure.invalid();
        }
        long page = number(request.getParameter("page"), 0);
        long size = number(request.getParameter("size"), 20);
        if (size < 1 || size > 100 || page * size > Integer.MAX_VALUE) throw CampusFailure.invalid();
        String[] requested = request.getParameterValues("sort");
        List<String> entries = requested == null ? List.of(defaultSort) : List.of(requested);
        if (entries.size() > 3) throw CampusFailure.invalid();
        var seen = new HashSet<String>();
        var effective = new ArrayList<String>();
        for (String entry : entries) {
            String[] parts = entry.split(",", -1);
            if (parts.length != 2 || !sortFields.contains(parts[0]) || !Set.of("asc", "desc").contains(parts[1]) || !seen.add(parts[0]))
                throw CampusFailure.invalid();
            effective.add(entry);
        }
        if (!seen.contains("id")) effective.add("id,asc");
        String search = request.getParameter("search");
        search = search == null || search.isBlank() ? null : search.trim();
        if (search != null && search.length() > 200) throw CampusFailure.invalid();
        return new CampusPageQuery(request, (int) page, (int) size, List.copyOf(effective), search);
    }

    public Pageable pageable() {
        return PageRequest.of(page, size, Sort.by(sort.stream().map(entry -> {
            String[] pair = entry.split(",");
            return new Sort.Order(pair[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, pair[0]);
        }).toList()));
    }
    public String search() { return search; }
    public Long id(String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        long parsed = number(value, 0);
        if (parsed <= 0) throw CampusFailure.invalid();
        return parsed;
    }
    public Integer integer(String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        if (!value.matches("-?[0-9]{1,5}")) throw CampusFailure.invalid();
        return Integer.valueOf(value);
    }
    public Boolean bool(String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        if (!value.equals("true") && !value.equals("false")) throw CampusFailure.invalid();
        return Boolean.valueOf(value);
    }
    public <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        String value = request.getParameter(name);
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw CampusFailure.invalid();
        }
    }
    public <S, T> CampusPageResponse<T> response(Page<S> result, Function<S, T> mapper) {
        return new CampusPageResponse<>(result.getContent().stream().map(mapper).toList(), page, size, result.getTotalElements(),
                result.getTotalPages(), page == 0, page >= result.getTotalPages() - 1, sort);
    }
    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,12}")) throw CampusFailure.invalid();
        return Long.parseLong(value);
    }
}
