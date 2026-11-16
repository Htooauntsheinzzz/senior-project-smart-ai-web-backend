package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.math.BigDecimal;

// Unknown metrics are null, never zero. creditsEarned covers recorded course results only (no transfer records exist).
public record StudentAcademicProgressResponse(BigDecimal cgpa, BigDecimal creditsEarned, Integer currentCourses,
        Integer currentEnrolledCredits, Integer studyYear, Integer academicYear, String dataStatus) {}
