package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

/**
 * Step 11: what the backend allows after the academic tools of a request have run. allowedToAnswer: the verified
 * facts may go to Gemini for the final answer. Otherwise safeMessage is the deterministic reply and no further
 * provider call is made. reasonCode is internal (logs and tests), never sent to Gemini or the student.
 */
public record AcademicGroundingDecision(boolean allowedToAnswer, boolean clarificationRequired, String reasonCode,
        String safeMessage) {
    public static final String ALLOW_GROUNDED_ANSWER = "ALLOW_GROUNDED_ANSWER";
    public static final String ASK_FOR_CLARIFICATION = "ASK_FOR_CLARIFICATION";
    public static final String INFORMATION_NOT_AVAILABLE = "INFORMATION_NOT_AVAILABLE";
    public static final String ACADEMIC_VERIFICATION_FAILED = "ACADEMIC_VERIFICATION_FAILED";
    public static final String BLOCK_UNSUPPORTED_CLAIM = "BLOCK_UNSUPPORTED_CLAIM";

    public static AcademicGroundingDecision allow() {
        return new AcademicGroundingDecision(true, false, ALLOW_GROUNDED_ANSWER, null);
    }
    public static AcademicGroundingDecision clarify(String message) {
        return new AcademicGroundingDecision(false, true, ASK_FOR_CLARIFICATION, message);
    }
    public static AcademicGroundingDecision notAvailable(String message) {
        return new AcademicGroundingDecision(false, false, INFORMATION_NOT_AVAILABLE, message);
    }
    public static AcademicGroundingDecision verificationFailed(String message) {
        return new AcademicGroundingDecision(false, false, ACADEMIC_VERIFICATION_FAILED, message);
    }
    public static AcademicGroundingDecision blockUnsupported(String message) {
        return new AcademicGroundingDecision(false, false, BLOCK_UNSUPPORTED_CLAIM, message);
    }
}
