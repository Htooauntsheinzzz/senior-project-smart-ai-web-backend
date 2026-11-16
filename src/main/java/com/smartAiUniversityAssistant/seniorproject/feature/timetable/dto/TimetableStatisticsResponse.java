package com.smartAiUniversityAssistant.seniorproject.feature.timetable.dto;

public record TimetableStatisticsResponse(long totalMeetings, long publishedMeetings, long draftMeetings, long cancelledMeetings,
        int conflictCount) {}
