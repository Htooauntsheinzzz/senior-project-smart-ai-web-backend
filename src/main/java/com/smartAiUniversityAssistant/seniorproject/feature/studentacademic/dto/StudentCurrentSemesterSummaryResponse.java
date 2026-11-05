package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

// Counts are null when the Student has no current semester on record.
public record StudentCurrentSemesterSummaryResponse(AcademicSemesterResponse semester, Integer courseCount,
        Integer enrolledCredits, Integer lecturerCount) {}
