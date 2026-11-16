package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.data.domain.*;

/** Shared strict parsing of the knowledge list parameters (same contract as the other admin lists). */
final class ListQuerySupport {
    private ListQuerySupport() {}

    static void onlyKnown(HttpServletRequest request, Set<String> parameters) {
        for (var parameter : request.getParameterMap().entrySet()) {
            if (!parameters.contains(parameter.getKey())
                    || !parameter.getKey().equals("sort") && parameter.getValue().length != 1) throw KnowledgeFailure.invalid();
        }
    }

    static List<String> sort(List<String> requested, Set<String> fields) {
        List<String> entries = requested == null || requested.isEmpty() ? List.of("createdAt,desc") : requested;
        if (entries.size() > 3) throw KnowledgeFailure.invalid();
        var seen = new HashSet<String>();
        var effective = new ArrayList<String>();
        for (String entry : entries) {
            String[] parts = entry == null ? new String[0] : entry.split(",", -1);
            if (parts.length != 2 || !fields.contains(parts[0])
                    || !Set.of("asc", "desc").contains(parts[1]) || !seen.add(parts[0])) throw KnowledgeFailure.invalid();
            effective.add(entry);
        }
        if (!seen.contains("id")) effective.add("id,asc");
        return List.copyOf(effective);
    }

    static Pageable pageable(int page, int size, List<String> sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw KnowledgeFailure.invalid();
        return PageRequest.of(page, size, Sort.by(sort.stream().map(entry -> {
            String[] pair = entry.split(",");
            return new Sort.Order(pair[1].equals("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, pair[0]);
        }).toList()));
    }

    static int page(HttpServletRequest request, String name, int fallback) {
        try {
            return Math.toIntExact(number(request.getParameter(name), fallback));
        } catch (ArithmeticException e) {
            throw KnowledgeFailure.invalid();
        }
    }

    static Long id(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        long id = number(value, 0);
        if (id <= 0) throw KnowledgeFailure.invalid();
        return id;
    }

    static Boolean bool(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        if (value == null) return null;
        if (!value.equals("true") && !value.equals("false")) throw KnowledgeFailure.invalid();
        return Boolean.valueOf(value);
    }

    static String search(String value, int max) {
        String search = value == null || value.isBlank() ? null : value.trim();
        if (search != null && search.length() > max) throw KnowledgeFailure.invalid();
        return search;
    }

    private static long number(String value, long fallback) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,18}")) throw KnowledgeFailure.invalid();
        return Long.parseLong(value);
    }
}
