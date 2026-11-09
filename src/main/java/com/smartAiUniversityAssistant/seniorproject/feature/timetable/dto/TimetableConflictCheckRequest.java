package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import jakarta.validation.constraints.*;
import java.time.*;

/** Pre-save check: the Add/Update fields plus the meeting being edited, so it does not conflict with itself. */
public record TimetableConflictCheckRequest(@Positive Long meetingId, @NotNull @Positive Long courseSectionId, @Positive Long roomId,
        @NotNull CourseMeetingType meetingType, @NotNull @Min(1) @Max(7) Integer dayOfWeek,
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime startTime, @NotNull @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        @NotNull LocalDate effectiveFrom, @NotNull LocalDate effectiveTo) {}
