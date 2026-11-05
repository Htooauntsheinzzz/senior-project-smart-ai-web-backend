package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.util.List;

// total counts the whole requested window; items is cut to the limit. dueTodayCount is always "from now until tonight".
public record StudentDeadlinesResponse(long total, long dueTodayCount, List<StudentDeadlineItemResponse> items) {}
