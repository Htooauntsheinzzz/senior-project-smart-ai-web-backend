package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import java.util.List;

/**
 * Step 11: the backend's check of a generated answer against the current request's evidence. issues names the kinds
 * of claim that failed (COURSE_CODE, SECTION, CREDIT_HOURS, SEATS, ROOM, LECTURER, PROGRAM, SEMESTER, YEAR, TIME,
 * WEEKDAY), never the values, so it can be logged without student data.
 */
public record GroundingValidationResult(Outcome outcome, List<String> issues) {

    public enum Outcome {
        /** Every deterministically checkable claim is supported by the evidence. */
        GROUNDED,
        /** A course, section, room, lecturer, semester, year, time or day that no evidence of this request contains. */
        UNSUPPORTED_ACADEMIC_CLAIM,
        /** No academic tool ran for this request. */
        MISSING_REQUIRED_EVIDENCE,
        /** A credit, seat, room, semester, program or lecturer value that contradicts the evidence of the same kind. */
        INCONSISTENT_ACADEMIC_VALUE,
        /** Academic claims while no query of this request succeeded. */
        UNVERIFIABLE_ANSWER
    }

    public GroundingValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static GroundingValidationResult grounded() { return new GroundingValidationResult(Outcome.GROUNDED, List.of()); }

    public boolean valid() { return outcome == Outcome.GROUNDED; }
}
