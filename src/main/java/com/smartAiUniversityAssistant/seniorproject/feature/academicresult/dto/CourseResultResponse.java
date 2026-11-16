package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CourseResultResponse(Long id, Long enrollmentId, Long studentId, String studentCode,
        Long courseId, String courseCode, String courseName, Long courseSectionId, String sectionNumber,
        Long semesterId, Long gradingSchemeId, String gradingSchemeCode, Long gradeDefinitionId, String gradeCode,
        BigDecimal gradePoint, BigDecimal creditsAttempted, String resultStatus,
        Instant finalizedAt, Long finalizedBy, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
