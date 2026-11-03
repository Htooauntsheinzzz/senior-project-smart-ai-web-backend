package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;

/**
 * The graded outcome of one attempt (case x trial). Boolean fields are null when the rule does not apply (no tool
 * expected, no arguments required, provider failure). actualIntent and intentCorrect stay null: the assistant exposes no
 * intent label (Step 8, D49), and two intents share a tool each, so none is guessed from the tool name.
 * understood: the semantic task was identified (an accepted tool set with correct arguments, or for edge cases the
 * expected behaviour). toolCorrect is strict (exactly the expected tool set); semanticToolCorrect also accepts the
 * case's declared alternatives, such as get_my_courses for an enrollment question.
 */
public record IntentEvaluationResult(String caseId, int trial, String category, String intent, String split, String language,
        String variationType, String testStudent, String input, String expectedBehavior, String expectedPrimaryTool,
        List<String> expectedTools, List<String> actualTools, List<Map<String, Object>> toolArguments,
        List<Map<String, Object>> toolExecutionStatus, String actualIntent, Boolean intentCorrect, Boolean toolCorrect,
        Boolean semanticToolCorrect, Boolean argumentsCorrect, Boolean understood, Boolean responseFactuallyCorrect,
        boolean taskCompleted, boolean hallucinationDetected, List<String> unsupportedClaims, boolean crossStudentLeakage,
        boolean secretExposed, boolean dataModified, boolean guessedIdentifier, boolean providerFailure, int httpStatus,
        String errorCode, List<String> failureCategories, String finalResponse, String requestId, String model,
        int providerRequests, long durationMs) {

    public boolean passed() { return !providerFailure && taskCompleted; }

    /** Observed tool set as one label for the routing confusion matrix. */
    public String routingLabel() {
        if (providerFailure) return "PROVIDER_FAILURE";
        if (actualTools.isEmpty()) return "(no tool)";
        return String.join("+", actualTools);
    }
}
