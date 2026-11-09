package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableConflictType;
import java.time.*;

/** One conflict found by the pre-save check: the first overlapping occurrence and the resource it is about. */
public record TimetableConflict(TimetableConflictType type, Long conflictingMeetingId, String message, LocalDate date,
        @JsonFormat(pattern = "HH:mm") LocalTime overlapStart, @JsonFormat(pattern = "HH:mm") LocalTime overlapEnd,
        Long roomId, Long lecturerId, Long courseSectionId) {}
