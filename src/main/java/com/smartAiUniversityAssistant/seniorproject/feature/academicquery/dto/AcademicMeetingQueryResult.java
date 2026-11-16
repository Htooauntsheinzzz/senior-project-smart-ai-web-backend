package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

import java.time.*;

/** One published weekly meeting as structured data for the assistant: it never parses schedule text. */
public record AcademicMeetingQueryResult(String meetingType, String dayOfWeek, LocalTime startTime, LocalTime endTime,
        LocalDate effectiveFrom, LocalDate effectiveTo, String roomNumber, String buildingName) {}
