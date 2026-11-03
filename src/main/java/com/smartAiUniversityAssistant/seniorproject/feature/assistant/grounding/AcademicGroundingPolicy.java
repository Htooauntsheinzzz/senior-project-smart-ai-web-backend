package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

/**
 * Step 11: backend-controlled rules for when an answer must rest on trusted academic evidence. Never relies on a
 * model-supplied intent label alone: the student's message and the tools that actually ran also trigger grounding.
 * General questions ("What does GPA mean?") need no evidence.
 */
public interface AcademicGroundingPolicy {

    /** Whether an academic intent label needs tool evidence: every Step 8 intent does; null or unknown labels do not. */
    boolean requiresAcademicEvidence(String academicIntent);

    /** Whether the student's message itself asks for personal or current academic facts (or names a course/section). */
    boolean messageRequiresAcademicEvidence(String message);

    /** Whether a tool's result is academic evidence that the final answer must be checked against. */
    boolean toolRequiresGrounding(String toolName);

    /**
     * Whether the message asks for date-relative schedule reasoning ("What is my next class?", "What classes do I have
     * tomorrow?") that the free-text course_sections.schedule cannot answer reliably.
     */
    boolean isUnsupportedScheduleQuestion(String message);
}
