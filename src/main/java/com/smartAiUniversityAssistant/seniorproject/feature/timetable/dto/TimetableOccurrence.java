package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import java.time.*;

/**
 * One dated occurrence of a recurring meeting after exceptions are applied. kind is REGULAR, RESCHEDULED (a moved
 * occurrence at its new date, time and room; originalDate says which one) or CANCELLED (kept only when a caller asks).
 */
public record TimetableOccurrence(Long meetingId, LocalDate date, LocalTime startTime, LocalTime endTime, Long roomId,
        String kind, Long exceptionId, LocalDate originalDate) {
    public static final String REGULAR = "REGULAR";
    public static final String RESCHEDULED = "RESCHEDULED";
    public static final String CANCELLED = "CANCELLED";
    public boolean overlaps(TimetableOccurrence other) {
        return date.equals(other.date) && startTime.isBefore(other.endTime) && endTime.isAfter(other.startTime);
    }
}
