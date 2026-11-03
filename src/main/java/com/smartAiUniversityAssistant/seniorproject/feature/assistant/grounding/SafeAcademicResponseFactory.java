package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import java.util.List;

/**
 * Step 11: deterministic student-facing replies for when an academic answer cannot or must not come from Gemini's
 * text. No LLM is called to phrase them. groundedAnswer renders only verified evidence of the current request, so it
 * can replace a generated answer that failed validation.
 */
public interface SafeAcademicResponseFactory {

    String lecturerUnavailable(String courseCode);

    String roomUnavailable(String courseCode);

    String scheduleUnavailable();

    String courseNotFound(String courseIdentifier);

    /** The student has no active enrollment in the named course. */
    String courseEnrollmentNotFound(String courseIdentifier);

    String sectionNotFound(String courseSectionId);

    /** A successful query found no active enrollments for the student. */
    String enrollmentUnavailable();

    String academicServiceUnavailable();

    String clarificationRequired();

    /** Clarification listing the backend's candidate courses (code and name only). */
    String ambiguousCourse(String courseIdentifier, List<?> candidates);

    /** A personal academic question still answered without any tool evidence (Step 7 fallback). */
    String missingEvidence();

    /** Date-relative schedule questions that free-text schedules cannot answer. */
    String unsupportedScheduleReasoning();

    /**
     * A deterministic answer built only from the verified evidence. With none: the service-unavailable reply after a
     * database failure, otherwise the missing-evidence reply.
     */
    String groundedAnswer(List<AcademicEvidence> evidence);
}
