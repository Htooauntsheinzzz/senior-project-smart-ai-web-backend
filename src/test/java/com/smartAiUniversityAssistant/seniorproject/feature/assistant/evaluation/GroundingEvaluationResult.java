package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.List;

/**
 * The graded outcome of one grounding case (its last turn). supported: the answer has no unsupported fixture entity, no
 * forbidden value and no other student's data. backendReplaced: the student received something other than the scripted
 * model's text (a validated fallback or a deterministic backend reply).
 */
public record GroundingEvaluationResult(String caseId, String scenario, String modelBehavior, String expectedOutcome,
        String input, String finalResponse, List<String> toolNames, List<String> failedToolCodes, int providerRequests,
        int httpStatus, boolean supported, List<String> unsupportedClaims, List<String> forbiddenFound,
        boolean crossStudentData, boolean outcomeMet, boolean backendReplaced, boolean passed, List<String> failures) {}
