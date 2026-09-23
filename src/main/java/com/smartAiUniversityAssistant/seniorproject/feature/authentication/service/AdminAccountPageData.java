package com.smartAiUniversityAssistant.seniorproject.feature.authentication.service;

import java.util.List;

public record AdminAccountPageData(List<AdminAccountData> content, long totalElements, int totalPages) {
    public AdminAccountPageData {
        content = content == null ? List.of() : List.copyOf(content);
    }
}
