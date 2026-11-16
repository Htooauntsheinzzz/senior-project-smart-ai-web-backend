package com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.dto;

public record StudentHomeSummaryResponse(StudentInfo student, StudentNextClassResponse nextClass, StudentDeadlinesResponse deadlines) {
    public record StudentInfo(String displayName, String programName, Integer studyYear) {}
}
