package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.dto;

import java.time.*;

public record CourseExamResponse(Long id, Long courseSectionId, String sectionNumber, Long courseId, String courseCode,
        String courseName, String title, String examType, OffsetDateTime startsAt, OffsetDateTime endsAt, String status,
        Long roomId, String roomLabel, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
