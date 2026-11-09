package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Several weekly meetings of one section, created atomically as drafts (spec file 42, section 30). */
public record TimetableBulkMeetingRequest(@NotNull @Positive Long courseSectionId,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull TimetableMeetingSlot> meetings) {}
