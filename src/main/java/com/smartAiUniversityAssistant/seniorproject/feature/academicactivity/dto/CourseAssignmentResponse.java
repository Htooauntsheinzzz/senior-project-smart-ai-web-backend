package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto;

import java.time.*;

public record CourseAssignmentResponse(Long id, Long courseSectionId, String sectionNumber, Long courseId, String courseCode,
        String courseName, String title, String description, OffsetDateTime dueAt, String status,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
