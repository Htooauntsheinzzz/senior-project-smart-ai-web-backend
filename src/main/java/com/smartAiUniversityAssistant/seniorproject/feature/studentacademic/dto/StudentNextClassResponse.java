package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.time.OffsetDateTime;

// buildingName is null until a Campus module exists. scheduleBasis says this reads the regular weekly timetable only:
// no holiday or cancellation data exists yet.
public record StudentNextClassResponse(Long courseId, String courseCode, String courseName, Long courseSectionId,
        String sectionNumber, String meetingType, String dayOfWeek, OffsetDateTime startsAt, OffsetDateTime endsAt,
        String buildingName, String roomName, Long minutesUntilStart, String status, String scheduleBasis) {}
