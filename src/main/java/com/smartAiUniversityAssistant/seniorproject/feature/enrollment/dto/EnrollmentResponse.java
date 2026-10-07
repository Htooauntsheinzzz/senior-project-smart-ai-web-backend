package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.*;

public record EnrollmentResponse(Long id,
        Long studentId, String studentCode, String studentFirstName, String studentLastName, String universityEmail,
        Long courseId, String courseCode, String courseName,
        Long courseSectionId, String sectionNumber,
        Long semesterId, String semesterNameEn, String semesterNameTh,
        Long lectureId, String lectureNameEn, String lectureNameTh,
        String room, String schedule,
        Integer capacity, long enrolledCount, @JsonProperty("isFull") boolean isFull,
        LocalDate enrollmentDate, String status,
        Long createdBy, Instant createdAt, Long updatedBy, Instant updatedAt) {}
