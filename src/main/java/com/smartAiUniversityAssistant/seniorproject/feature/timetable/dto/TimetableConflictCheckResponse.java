package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import java.util.List;

public record TimetableConflictCheckResponse(boolean hasConflicts, int totalConflicts, List<TimetableConflict> conflicts) {}
