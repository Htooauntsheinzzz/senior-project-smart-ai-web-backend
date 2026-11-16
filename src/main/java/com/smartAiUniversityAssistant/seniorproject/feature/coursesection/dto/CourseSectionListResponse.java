package com.smartAiUniversityAssistant.seniorproject.feature.coursesection.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.LocalDate;

public record CourseSectionListResponse(Long id,
        Long courseId, String courseCode, String courseName,
        String sectionNumber,
        Long lectureId, String lectureNameEn, String lectureNameTh,
        Integer capacity, long enrolledCount, @JsonProperty("isFull") boolean isFull,
        Long semesterId, String semesterNameEn, String semesterNameTh,
        Long academicTermId, String academicYearCode, String academicYearLabel,
        // Spec file 44: scheduled = has a published timetable meeting; enrollmentOpen = scheduled and the due date has not passed.
        boolean scheduled, LocalDate enrollmentDueDate, boolean enrollmentOpen,
        String status, Instant createdAt, Instant updatedAt) {}
