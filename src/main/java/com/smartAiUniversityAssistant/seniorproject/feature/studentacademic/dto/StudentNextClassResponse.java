package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.time.*;

/**
 * roomName and buildingName come from the campus room (roomName falls back to the legacy label). scheduleBasis is
 * PUBLISHED_TIMETABLE: published meetings with one-day cancellations and reschedules applied. occurrenceStatus is
 * REGULAR or RESCHEDULED (then originalDate says which occurrence moved).
 */
public record StudentNextClassResponse(Long courseId, String courseCode, String courseName, Long courseSectionId,
        String sectionNumber, String meetingType, String dayOfWeek, OffsetDateTime startsAt, OffsetDateTime endsAt,
        String buildingName, String roomName, Long minutesUntilStart, String status, String scheduleBasis,
        Long meetingId, Long roomId, String roomNumber, Long buildingId, Long campusId, String occurrenceStatus, LocalDate originalDate) {}
