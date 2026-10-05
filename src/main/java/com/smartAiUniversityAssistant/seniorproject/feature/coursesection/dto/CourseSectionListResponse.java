package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CourseSectionListResponse(Long id,
        Long courseId, String courseCode, String courseName,
        String sectionNumber,
        Long lectureId, String lectureNameEn, String lectureNameTh,
        String room, String schedule,
        Integer capacity, long enrolledCount, @JsonProperty("isFull") boolean isFull,
        Long semesterId, String semesterNameEn, String semesterNameTh,
        String status, Instant createdAt, Instant updatedAt) {}
