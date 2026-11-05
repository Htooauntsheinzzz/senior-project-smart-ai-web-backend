package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;

// roomName is the meeting's own label; null when none was recorded. dayOfWeek is MONDAY..SUNDAY.
public record StudentCourseMeetingResponse(String meetingType, String dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        LocalDate effectiveFrom, LocalDate effectiveTo, String roomName) {}
