package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;

/** One Step 15 case: one question on a new conversation, the stand-in model's script, and what the Student must get. */
public record CurriculumEvaluationCase(String caseId, Category category, String language, String testStudent,
        String input, String modelBehavior, List<Map<String, Object>> script, Outcome expectedOutcome,
        List<String> mustContain, List<String> forbiddenFacts, List<String> expectedSources, List<String> expectedTools,
        Integer expectedProviderRequests, boolean knownLimit, String note) {

    public enum Category { GRADUATION, ELECTIVES, INTERNSHIP, PROGRAM_REQUIREMENTS, PRECEDENCE, OTHER_AUDIENCE,
        NOT_FOUND, SEARCH_MISSING, FAULTY_ANSWER, PERSONAL_MIX, LANGUAGE }

    /** ANSWER: the last scripted text unchanged. PASSAGES: the backend's passage block. NOT_FOUND: knowledgeNotFound().
     *  MISSING_EVIDENCE: the reply ends with knowledgeMissingEvidence(). */
    public enum Outcome { ANSWER, PASSAGES, NOT_FOUND, MISSING_EVIDENCE }

    @SuppressWarnings("unchecked")
    static CurriculumEvaluationCase fromMap(Map<String, Object> m) {
        return new CurriculumEvaluationCase((String) m.get("caseId"), Category.valueOf((String) m.get("category")),
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
