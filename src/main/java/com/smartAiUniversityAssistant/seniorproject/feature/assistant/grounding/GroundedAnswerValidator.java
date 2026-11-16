package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import java.util.List;

/**
 * Step 11: deterministic check of Gemini's final answer against the current request's evidence. It does not claim to
 * detect every semantic hallucination: it checks the claims that can be verified reliably (course codes, section
 * numbers, credit hours, seat counts, rooms, lecturer names, programs, semesters, years, class times and weekdays)
 * and never asks a model whether the answer is correct. A failed check means the answer is replaced by a deterministic
 * grounded one.
 */
public interface GroundedAnswerValidator {

    GroundingValidationResult validate(String generatedAnswer, List<AcademicEvidence> evidence);

    /**
     * Whether an answer states specific university facts (a course code, room, section, lecturer or program) that
     * need evidence even when the question looked general: such an answer without any tool run is not returned.
     * Credit, seat and time numbers alone do not count, so general explanations stay possible.
     */
    boolean containsUniversityFacts(String answer);
}
