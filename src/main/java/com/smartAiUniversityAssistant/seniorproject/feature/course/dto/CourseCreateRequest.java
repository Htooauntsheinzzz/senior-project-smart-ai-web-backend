package com.smartAiUniversityAssistant.seniorproject.feature.course.dto;

import jakarta.validation.constraints.*;

public record CourseCreateRequest(
        @NotBlank @Size(max = 50) String courseCode,
        @NotBlank @Size(max = 255) String courseName,
        @NotNull @Positive Integer creditHours,
        @Size(max = 1000) String description,
        @NotNull @Positive Long facultyId,
        @NotNull @Positive Long departmentId,
        @Positive Long programId,
        @Positive Long semesterId,
        @Positive Integer recommendedAcademicYear,
        @Size(max = 1000) String prerequisiteCourses,
        @NotNull Integer courseType,
        @Positive Integer maximumStudentsPerSection,
        Boolean isActive) {
    public CourseCreateRequest {
        courseCode = courseCode == null ? null : courseCode.trim();
        courseName = courseName == null ? null : courseName.trim();
        description = description == null || description.isBlank() ? null : description.trim();
        prerequisiteCourses = prerequisiteCourses == null || prerequisiteCourses.isBlank() ? null : prerequisiteCourses.trim();
    }
}
