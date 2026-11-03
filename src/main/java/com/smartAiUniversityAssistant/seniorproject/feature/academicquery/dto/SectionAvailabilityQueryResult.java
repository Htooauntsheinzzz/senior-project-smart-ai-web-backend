package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// occupiedSeats counts non-deleted PENDING and ACTIVE enrollments; availableSeats never goes below zero.
public record SectionAvailabilityQueryResult(Long courseSectionId, Long courseId, String courseCode, String courseName,
        String sectionNumber, Integer capacity, long occupiedSeats, long availableSeats,
        @JsonProperty("isFull") boolean isFull, String sectionStatus,
        Long semesterId, Integer semesterAcademicYear, String semesterNameEn, String semesterNameTh) {}
