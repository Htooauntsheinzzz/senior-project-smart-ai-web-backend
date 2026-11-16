package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

/**
 * How the current message relates to the conversation's academic reference, decided by the backend before the model
 * runs. courseCode is set only for ORDINAL and ACTIVE (a clear referent); requestedIndex is the list position the
 * student named (ORDINAL and OUT_OF_RANGE).
 */
public record ResolvedAcademicReference(Kind kind, AcademicConversationReference reference, String courseCode,
        Long courseSectionId, Integer requestedIndex) {

    public enum Kind {
        /** No earlier turn, or the message does not point back at a course. */
        NONE,
        /** The message names a course code itself; it overrides the earlier context. */
        EXPLICIT,
        /** "the first one", "number 2": a position in the list the student was shown. */
        ORDINAL,
        /** "it", "that course": the single course discussed last. */
        ACTIVE,
        /** A position that the shown list does not have, or a list position when no list was shown. */
        OUT_OF_RANGE,
        /** "it" when no single course is being discussed. */
        AMBIGUOUS
    }

    public boolean hasReferent() { return courseCode != null; }
}
