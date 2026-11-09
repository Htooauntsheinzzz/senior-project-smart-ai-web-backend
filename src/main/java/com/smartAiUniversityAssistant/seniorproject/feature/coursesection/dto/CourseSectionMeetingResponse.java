package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.*;

// status is authoritative (DRAFT, PUBLISHED, CANCELLED); active stays for older clients and equals status == PUBLISHED.
public record CourseSectionMeetingResponse(Long id, Long courseSectionId, String meetingType, Integer dayOfWeek,
        String dayName, @JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        LocalDate effectiveFrom, LocalDate effectiveTo, Long roomId, String roomNumber, String buildingName, String roomLabel,
        String status, boolean active, String notes, Instant createdAt, Instant updatedAt) {}
