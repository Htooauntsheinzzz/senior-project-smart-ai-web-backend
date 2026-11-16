package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;
import java.util.List;

/**
 * A published weekly meeting. dayOfWeek is MONDAY..SUNDAY (kept from spec file 40) and dayOfWeekNumber is 1..7.
 * roomName is the campus room number, or the legacy label for a meeting that predates campus rooms; the ids and
 * building come from the campus records (spec file 42, section 52). exceptions lists upcoming one-day changes.
 */
public record StudentCourseMeetingResponse(String meetingType, String dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        LocalDate effectiveFrom, LocalDate effectiveTo, String roomName,
        Long meetingId, Integer dayOfWeekNumber, Long roomId, String roomNumber, Long buildingId, String buildingName, Long campusId,
        List<StudentMeetingExceptionResponse> exceptions) {}
