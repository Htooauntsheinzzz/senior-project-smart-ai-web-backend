package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.enums.CourseMeetingType;
import com.smartAiUniversityAssistant.seniorproject.feature.timetable.enums.TimetableMeetingStatus;
import java.time.*;

public record TimetableMeetingResponse(Long id, TimetableNamedRef courseSection, TimetableNamedRef course, TimetableTermRef academicTerm,
        TimetableNamedRef faculty, TimetableNamedRef department, TimetableNamedRef lecturer, TimetableLocation location,
        CourseMeetingType meetingType, int dayOfWeek, String dayName,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        LocalDate effectiveFrom, LocalDate effectiveTo, LocalDate enrollmentDueDate, TimetableMeetingStatus status, String notes,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
