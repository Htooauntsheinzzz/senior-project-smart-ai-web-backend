package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import jakarta.validation.constraints.*;
import java.time.*;

/** The schedulable fields of one meeting: the update body, and one item of the bulk body. */
public record TimetableMeetingSlot(@Positive Long roomId, @NotNull CourseMeetingType meetingType,
        @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime, @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @NotNull LocalDate effectiveFrom, @NotNull LocalDate effectiveTo, @Size(max = 2000) String notes) {
    public TimetableMeetingSlot {
        notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
}
