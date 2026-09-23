package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.request;

import com.smartAiUniversityAssistant.seniorproject.feature.authentication.service.AdminAccountListQuery;
import com.smartAiUniversityAssistant.seniorproject.feature.user.exception.AdminUserQueryValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

public record AdminUserListQuery(String page, String size, String search, String accountStatus,
        String roleId, String departmentId, String departmentUnassigned, List<String> sort,
        Set<String> unknownParameters) {
    private static final Set<String> ALLOWED = Set.of("page", "size", "search", "accountStatus",
            "roleId", "departmentId", "departmentUnassigned", "sort");
    private static final Set<String> SORT_FIELDS = Set.of("id", "employeeId", "firstName", "lastName",
            "email", "accountStatus", "createdAt", "updatedAt");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE", "LOCKED", "SUSPENDED");

    public static AdminUserListQuery from(HttpServletRequest request) {
        Set<String> unknown = new LinkedHashSet<>();
        request.getParameterMap().keySet().forEach(name -> {
            if (!ALLOWED.contains(name)) unknown.add(name);
        });
        String[] sortValues = request.getParameterValues("sort");
        return new AdminUserListQuery(request.getParameter("page"), request.getParameter("size"),
                request.getParameter("search"), request.getParameter("accountStatus"),
                request.getParameter("roleId"), request.getParameter("departmentId"),
                request.getParameter("departmentUnassigned"),
                sortValues == null ? List.of() : List.of(sortValues), unknown);
    }

    public AdminAccountListQuery parse() {
        if (!unknownParameters.isEmpty()) throw invalid("Unknown query parameter.");
        int parsedPage = parseInt(page, 0, "page");
        if (parsedPage < 0) throw invalid("page must be zero or greater.");
        int parsedSize = parseInt(size, 50, "size");
        if (parsedSize < 1 || parsedSize > 50) throw invalid("size must be between 1 and 50.");
        String parsedSearch = search == null ? null : search.trim();
        if (parsedSearch != null && parsedSearch.isEmpty()) parsedSearch = null;
        if (parsedSearch != null && parsedSearch.length() > 255) throw invalid("search is too long.");
        String parsedStatus = accountStatus;
        if (parsedStatus != null && !STATUSES.contains(parsedStatus)) throw invalid("accountStatus is invalid.");
        Long parsedRoleId = parsePositiveLong(roleId, "roleId");
        Long parsedDepartmentId = parsePositiveLong(departmentId, "departmentId");
        boolean unassigned = parseBoolean(departmentUnassigned);
        if (unassigned && parsedDepartmentId != null)
            throw invalid("departmentUnassigned cannot be combined with departmentId.");
        return new AdminAccountListQuery(parsedPage, parsedSize, parsedSearch, parsedStatus,
                parsedRoleId, parsedDepartmentId, unassigned, parseSort());
    }

    private List<AdminAccountListQuery.Order> parseSort() {
        List<AdminAccountListQuery.Order> orders = new ArrayList<>();
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
            orders.add(new AdminAccountListQuery.Order(field, direction.equals("asc")));
        }
        if (!seen.contains("id")) orders.add(new AdminAccountListQuery.Order("id", true));
        return List.copyOf(orders);
    }

    private int parseInt(String value, int defaultValue, String field) {
        if (value == null) return defaultValue;
        if (!value.matches("\\d+")) throw invalid(field + " must be a non-negative integer.");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw invalid(field + " is out of range.");
        }
    }

    private Long parsePositiveLong(String value, String field) {
        if (value == null) return null;
        if (!value.matches("\\d+")) throw invalid(field + " must be a positive integer.");
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) throw invalid(field + " must be a positive integer.");
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid(field + " is out of range.");
        }
    }

    private boolean parseBoolean(String value) {
        if (value == null || value.equals("false")) return false;
        if (value.equals("true")) return true;
        throw invalid("departmentUnassigned must be true or false.");
    }

    private AdminUserQueryValidationException invalid(String message) {
        return new AdminUserQueryValidationException(message);
    }
}
