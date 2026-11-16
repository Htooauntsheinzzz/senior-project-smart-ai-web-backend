package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import java.time.Instant;

/**
 * Step 11: one academic tool execution of the current request, as trusted evidence. Created only by
 * {@link AcademicGroundingService#capture} from the backend tool executor's own result, never from model or student
 * text, and kept for the current request only (never stored in Redis, never reused across requests or students).
 * verifiedData: the tool's data as the model received it (a map or a list of maps) for VERIFIED/VERIFIED_EMPTY, the
 * backend's candidate courses for AMBIGUOUS, otherwise null. safeErrorCode: the tool's error code, null on success.
 * requestedIdentifier: the course identifier or section id the tool was queried with, null for tools without one.
 * Never exposed through the Student API.
 */
public record AcademicEvidence(String toolName, AcademicEvidenceStatus status, Object verifiedData, Instant verifiedAt,
        String safeErrorCode, String requestedIdentifier) {

    /** Whether the backend query succeeded (with or without records). */
    public boolean verified() {
        return status == AcademicEvidenceStatus.VERIFIED || status == AcademicEvidenceStatus.VERIFIED_EMPTY;
    }
}
