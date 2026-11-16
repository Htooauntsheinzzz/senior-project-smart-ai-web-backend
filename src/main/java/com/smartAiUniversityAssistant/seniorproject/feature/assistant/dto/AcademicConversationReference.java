package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import java.util.List;

/**
 * What the conversation is currently about: a pointer for resolving "it" and "the first one", never a copy of
 * academic facts. activeCourseCode is the single course last discussed (null when none or unclear);
 * lastPresentedCourses is the course list in the order the student saw it; lastAcademicIntent is the last tool
 * that ran successfully.
 */
public record AcademicConversationReference(String activeCourseCode, Long activeCourseSectionId,
        String lastAcademicIntent, List<PresentedCourseReference> lastPresentedCourses) {
    public static final AcademicConversationReference EMPTY = new AcademicConversationReference(null, null, null, List.of());

    public AcademicConversationReference {
        lastPresentedCourses = lastPresentedCourses == null ? List.of() : List.copyOf(lastPresentedCourses);
    }
}
