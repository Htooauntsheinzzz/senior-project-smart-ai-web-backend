package com.smartAiUniversityAssistant.seniorproject.feature.program.dto;

import java.util.List;

public record ProgramPageResponse(List<ProgramListResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last, List<String> sort) {}
