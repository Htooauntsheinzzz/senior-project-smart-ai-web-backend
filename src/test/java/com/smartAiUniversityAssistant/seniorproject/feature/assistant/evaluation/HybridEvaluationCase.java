package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;

/** One Step 16 case: one question on a new conversation, the stand-in model's script, and what the Student must get. */
public record HybridEvaluationCase(String caseId, Category category, String language, String testStudent,
        String input, String modelBehavior, List<Map<String, Object>> script, Outcome expectedOutcome,
        List<String> mustContain, List<String> forbiddenFacts, List<String> expectedSources, List<String> expectedTools,
        Integer expectedProviderRequests, boolean knownLimit, String note) {

    public enum Category { CREDITS_REMAINING, GPA_STANDING, COOP_ELIGIBILITY, GRADUATION_STANDING, MISSING_PROGRESS,
        NO_PROGRAM_DOCUMENT, DOCUMENT_NOT_FOUND, SEARCH_MISSING, FAULTY_CALCULATION, FAULTY_CONCLUSION,
        PROGRESS_UNAVAILABLE, NOT_HYBRID, LANGUAGE }

    /** ANSWER: the last scripted text unchanged. CHECKED_REPLY: progress part, passages, degreeAuditReferral().
     *  DOCUMENT_NOT_FOUND: progress part and knowledgeNotFound(). MISSING_EVIDENCE: ends with knowledgeMissingEvidence().
     *  SERVICE_UNAVAILABLE: academicServiceUnavailable(). */
    public enum Outcome { ANSWER, CHECKED_REPLY, DOCUMENT_NOT_FOUND, MISSING_EVIDENCE, SERVICE_UNAVAILABLE }

    @SuppressWarnings("unchecked")
    static HybridEvaluationCase fromMap(Map<String, Object> m) {
        return new HybridEvaluationCase((String) m.get("caseId"), Category.valueOf((String) m.get("category")),
                (String) m.get("language"), (String) m.get("testStudent"), (String) m.get("input"), (String) m.get("modelBehavior"),
                (List<Map<String, Object>>) m.get("script"), Outcome.valueOf((String) m.get("expectedOutcome")),
                list(m.get("mustContain")), list(m.get("forbiddenFacts")), (List<String>) m.get("expectedSources"),
                (List<String>) m.get("expectedTools"), (Integer) m.get("expectedProviderRequests"),
                Boolean.TRUE.equals(m.get("knownLimit")), (String) m.get("note"));
    }

    /** The text of the last scripted answer, the message an ANSWER case must return unchanged. */
    String lastText() {
        for (int i = script.size() - 1; i >= 0; i--) if (script.get(i).get("text") instanceof String text) return text;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object value) { return value == null ? List.of() : List.copyOf((List<String>) value); }
}
