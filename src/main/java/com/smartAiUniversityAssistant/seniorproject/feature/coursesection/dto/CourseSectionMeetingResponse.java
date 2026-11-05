package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;

public record CourseSectionMeetingResponse(Long id, Long courseSectionId, String meetingType, Integer dayOfWeek,
        String dayName, @JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        LocalDate effectiveFrom, LocalDate effectiveTo, Long roomId, String roomLabel, boolean active,
        Instant createdAt, Instant updatedAt) {}
