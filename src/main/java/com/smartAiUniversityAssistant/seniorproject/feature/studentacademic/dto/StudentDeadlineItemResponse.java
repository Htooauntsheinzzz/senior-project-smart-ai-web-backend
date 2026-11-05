package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

import java.time.OffsetDateTime;

// For an EXAM, deadlineAt is the exam's start time.
public record StudentDeadlineItemResponse(Long id, String type, String title, String courseCode, String courseName,
        OffsetDateTime deadlineAt, String status) {}
