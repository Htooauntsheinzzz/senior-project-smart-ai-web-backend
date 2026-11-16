package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableConflictType;
import java.time.*;

/** Two published meetings that share a room, lecturer or section on at least one real occurrence. */
public record TimetableConflictPair(TimetableConflictType type, TimetableConflictMeeting meetingA, TimetableConflictMeeting meetingB,
        LocalDate firstDate, @JsonFormat(pattern = "HH:mm") LocalTime overlapStart, @JsonFormat(pattern = "HH:mm") LocalTime overlapEnd,
        int occurrenceCount, Long roomId, String roomNumber, Long lecturerId, String lecturerName, Long courseSectionId) {}
