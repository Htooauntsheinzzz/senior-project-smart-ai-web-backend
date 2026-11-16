package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

import java.math.BigDecimal;

/** Step 16A: the totals of spec file 40 for the token's Student. Unknown values are null, never zero. */
public record AcademicProgressQueryResult(BigDecimal cgpa, BigDecimal creditsEarned, Integer currentCourses,
        Integer currentEnrolledCredits, Integer studyYear, Integer academicYear, String dataStatus) {}
