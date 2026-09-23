package com.smartAiUniversityAssistant.seniorproject.feature.user.dto.response;

import java.util.List;

public record AdminUserPageResponse(List<AdminUserSummaryResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
