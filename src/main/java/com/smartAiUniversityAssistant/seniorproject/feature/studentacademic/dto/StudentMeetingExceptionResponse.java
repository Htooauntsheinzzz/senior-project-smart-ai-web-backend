package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;

/** A cancelled or moved occurrence of one of the student's meetings. Replacement fields are null for a cancellation. */
public record StudentMeetingExceptionResponse(LocalDate originalDate, String exceptionType, LocalDate replacementDate,
        @JsonFormat(pattern = "HH:mm") LocalTime replacementStartTime, @JsonFormat(pattern = "HH:mm") LocalTime replacementEndTime,
        Long roomId, String roomNumber, String buildingName, String reason) {}
