package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import java.util.List;

public record TimetableConflictListResponse(int total, List<TimetableConflictPair> conflicts) {}
