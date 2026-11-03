package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

// One of the Courses an ambiguous identifier matched, so the assistant can ask which one the Student means.
public record AcademicCourseCandidate(Long courseId, String courseCode, String courseName) {}
