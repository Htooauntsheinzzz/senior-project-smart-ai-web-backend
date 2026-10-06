package com.smartAiUniversityAssistant.seniorproject.feature.course.dto;

public record CourseSummaryResponse(long totalCourses, long activeCourses, long inactiveCourses,
        long currentSemesterCourses) {}
