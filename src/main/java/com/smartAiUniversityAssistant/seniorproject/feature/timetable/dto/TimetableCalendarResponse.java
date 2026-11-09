package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import java.time.LocalDate;
import java.util.List;

public record TimetableCalendarResponse(Long academicTermId, LocalDate weekStart, LocalDate weekEnd, String timezone,
        List<TimetableCalendarEvent> events) {}
