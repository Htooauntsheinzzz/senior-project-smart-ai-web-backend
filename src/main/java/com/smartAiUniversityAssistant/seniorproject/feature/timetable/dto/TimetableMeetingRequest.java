package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import jakarta.validation.constraints.*;
import java.time.*;

/** Add Class Schedule (spec file 42, section 28). Course, lecturer, term, faculty, building and campus are derived. */
public record TimetableMeetingRequest(@NotNull @Positive Long courseSectionId, @Positive Long roomId,
        @NotNull CourseMeetingType meetingType, @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime, @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @NotNull LocalDate effectiveFrom, @NotNull LocalDate effectiveTo, @Size(max = 2000) String notes) {
    public TimetableMeetingRequest {
        notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
    public TimetableMeetingSlot slot() {
        return new TimetableMeetingSlot(roomId, meetingType, dayOfWeek, startTime, endTime, effectiveFrom, effectiveTo, notes);
    }
}
