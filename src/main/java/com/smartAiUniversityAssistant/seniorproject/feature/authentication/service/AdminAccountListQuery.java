package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import java.util.List;

public record AdminAccountListQuery(int page, int size, String search, String accountStatus,
        Long roleId, Long departmentId, boolean departmentUnassigned, List<Order> sort) {
    public AdminAccountListQuery {
        sort = sort == null ? List.of() : List.copyOf(sort);
    }
    public record Order(String field, boolean ascending) {}
}
