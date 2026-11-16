package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableExceptionType;
import java.time.*;

public record TimetableExceptionResponse(Long id, Long meetingId, LocalDate originalDate, TimetableExceptionType exceptionType,
        LocalDate replacementDate, @JsonFormat(pattern = "HH:mm") LocalTime replacementStartTime,
        @JsonFormat(pattern = "HH:mm") LocalTime replacementEndTime, Long replacementRoomId, String replacementRoomNumber,
        String replacementBuildingName, String reason, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
