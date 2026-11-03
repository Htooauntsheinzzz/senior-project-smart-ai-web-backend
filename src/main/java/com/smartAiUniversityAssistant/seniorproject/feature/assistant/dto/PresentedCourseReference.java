package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

/** A course in the numbered list the student was shown; displayIndex starts at 1. courseSectionId may be null. */
public record PresentedCourseReference(int displayIndex, String courseCode, Long courseSectionId) {}
