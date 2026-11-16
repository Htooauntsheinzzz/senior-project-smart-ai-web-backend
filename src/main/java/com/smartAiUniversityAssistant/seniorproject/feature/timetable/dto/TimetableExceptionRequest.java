package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableExceptionType;
import jakarta.validation.constraints.*;
import java.time.*;

/** CANCELLED: only originalDate (and reason). RESCHEDULED: replacement date and times; room optional (meeting's room). */
public record TimetableExceptionRequest(@NotNull LocalDate originalDate, @NotNull TimetableExceptionType exceptionType,
        LocalDate replacementDate, @JsonFormat(pattern = "HH:mm") LocalTime replacementStartTime,
        @JsonFormat(pattern = "HH:mm") LocalTime replacementEndTime, @Positive Long replacementRoomId,
        @Size(max = 500) String reason) {
    public TimetableExceptionRequest {
        reason = reason == null || reason.isBlank() ? null : reason.trim();
    }
}
