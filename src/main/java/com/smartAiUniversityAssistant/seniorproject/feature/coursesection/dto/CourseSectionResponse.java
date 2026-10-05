package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CourseSectionResponse(Long id,
        Long courseId, String courseCode, String courseName,
        String sectionNumber, Integer capacity, long enrolledCount, @JsonProperty("isFull") boolean isFull,
        Long lectureId, String lectureNameTh, String lectureNameEn, String lectureNickname,
        String room, String schedule,
        Long semesterId, String semesterNameTh, String semesterNameEn,
        String status, Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
