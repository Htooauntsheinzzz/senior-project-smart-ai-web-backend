package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

/**
 * Step 11: how one backend tool execution turned out, as academic evidence. Internal only; never sent to the
 * student. VERIFIED: trusted data was retrieved (single fields may still be null, meaning "not currently available").
 * VERIFIED_EMPTY: the query succeeded and returned no records. NOT_FOUND: the course, section or the student's
 * enrollment in it does not exist. AMBIGUOUS: more than one course matched. UNAVAILABLE: the academic service could
 * not provide the data right now (database failure). ERROR: verification failed for another reason (unsupported tool,
 * invalid arguments). Authorization failures never become evidence: they stop the request in the security layer.
 */
public enum AcademicEvidenceStatus {
    VERIFIED,
    VERIFIED_EMPTY,
    NOT_FOUND,
    AMBIGUOUS,
    UNAVAILABLE,
    ERROR
}
