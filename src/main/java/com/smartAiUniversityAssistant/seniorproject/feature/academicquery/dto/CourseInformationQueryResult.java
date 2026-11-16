package com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto;

// Non-private Course metadata only: no audit columns. prerequisiteCourses is the stored text, not an eligibility check.
public record CourseInformationQueryResult(Long courseId, String courseCode, String courseName, Integer creditHours,
        String description, Integer courseType, String courseTypeName, String prerequisiteCourses,
        Integer maximumStudentsPerSection, Integer recommendedAcademicYear, boolean active,
        Long facultyId, String facultyCode, String facultyNameEn, String facultyNameTh,
        Long departmentId, String departmentCode, String departmentName,
        Long programId, String programCode, String programName) {}
