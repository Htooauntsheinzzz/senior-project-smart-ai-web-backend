package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

public record CourseSectionSummaryResponse(long totalSections, long activeSections, long closedSections,
        long fullSections, long totalEnrolled) {}
