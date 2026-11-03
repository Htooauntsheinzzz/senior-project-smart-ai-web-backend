package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;

/**
 * One Step 11 grounding case (src/test/resources/assistant/step11/grounding-cases.jsonl). turns are sent in order on
 * one conversation; only the last answer is graded. script is the stand-in model of that turn (same format as Step 9's
 * ScriptedLlmClient); modelBehavior says whether it answers honestly or FAULTY (invents, contradicts or reuses stale or
 * another student's facts), so the case shows what the backend returns either way. setup and mutateBefore name
 * controlled admin changes of the fixture, failTools the academic queries that fail like a database outage.
 */
public record GroundingEvaluationCase(String caseId, Scenario scenario, String testStudent, String modelBehavior,
        List<Turn> turns, Outcome expectedOutcome, List<String> expectedFacts, List<String> mustContain,
        List<String> forbiddenFacts, List<String> setup, List<String> failTools, Integer expectedProviderRequests) {

    /** Spec §68 grounding scenarios. */
    public enum Scenario { VERIFIED_FACTS, MISSING_DATA, TOOL_FAILURE, AMBIGUOUS, MULTI_FACT, CONVERSATION_FRESHNESS, PROMPT_INJECTION }

    /** ANSWER with facts, NO_DATA, CLARIFY, VERIFICATION_FAILED, MISSING_EVIDENCE (Step 7 fallback), SAFE (no violation). */
    public enum Outcome { ANSWER, NO_DATA, CLARIFY, VERIFICATION_FAILED, MISSING_EVIDENCE, SAFE }

    public record Turn(String input, List<Map<String, Object>> script, List<String> mutateBefore) {
        public Turn {
            script = script == null ? List.of() : List.copyOf(script);
            mutateBefore = mutateBefore == null ? List.of() : List.copyOf(mutateBefore);
        }
    }

    public GroundingEvaluationCase {
        turns = List.copyOf(turns);
        expectedFacts = expectedFacts == null ? List.of() : List.copyOf(expectedFacts);
        mustContain = mustContain == null ? List.of() : List.copyOf(mustContain);
        forbiddenFacts = forbiddenFacts == null ? List.of() : List.copyOf(forbiddenFacts);
        setup = setup == null ? List.of() : List.copyOf(setup);
        failTools = failTools == null ? List.of() : List.copyOf(failTools);
    }

    @SuppressWarnings("unchecked")
    static GroundingEvaluationCase fromMap(Map<String, Object> m) {
        var turns = new ArrayList<Turn>();
        for (var t : (List<Map<String, Object>>) m.get("turns"))
            turns.add(new Turn((String) t.get("input"), (List<Map<String, Object>>) t.get("script"), (List<String>) t.get("mutateBefore")));
        return new GroundingEvaluationCase((String) m.get("caseId"), Scenario.valueOf((String) m.get("scenario")),
                (String) m.get("testStudent"), (String) m.get("modelBehavior"), turns, Outcome.valueOf((String) m.get("expectedOutcome")),
                (List<String>) m.get("expectedFacts"), (List<String>) m.get("mustContain"), (List<String>) m.get("forbiddenFacts"),
                (List<String>) m.get("setup"), (List<String>) m.get("failTools"), (Integer) m.get("expectedProviderRequests"));
    }

    public boolean mutatesData() { return !setup.isEmpty() || turns.stream().anyMatch(t -> !t.mutateBefore().isEmpty()); }

    public Turn finalTurn() { return turns.getLast(); }

    /** The final turn's scripted text answer (what the model said), or null when its script never answers in text. */
    public String scriptedAnswer() {
        var script = finalTurn().script();
        return script.isEmpty() ? null : (String) script.getLast().get("text");
    }

    /** A copy with ${NAME} placeholders replaced by the fixture's generated values. */
    @SuppressWarnings("unchecked")
    public GroundingEvaluationCase resolve(Map<String, Object> placeholders) {
        var resolved = turns.stream().map(t -> new Turn((String) replace(t.input(), placeholders, true),
                (List<Map<String, Object>>) replace(t.script(), placeholders, false), t.mutateBefore())).toList();
        return new GroundingEvaluationCase(caseId, scenario, testStudent, modelBehavior, resolved, expectedOutcome, expectedFacts,
                (List<String>) replace(mustContain, placeholders, true), (List<String>) replace(forbiddenFacts, placeholders, true),
                setup, failTools, expectedProviderRequests);
    }

    // ${FROM_PREVIOUS} is left for the scripted model; a whole-value placeholder in a script keeps its type (a number).
    private static Object replace(Object value, Map<String, Object> placeholders, boolean inText) {
        if (value instanceof String s) {
            if (!inText && s.startsWith("${") && s.endsWith("}") && placeholders.containsKey(s.substring(2, s.length() - 1)))
                return placeholders.get(s.substring(2, s.length() - 1));
            String out = s;
            for (var e : placeholders.entrySet()) out = out.replace("${" + e.getKey() + "}", String.valueOf(e.getValue()));
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            var out = new LinkedHashMap<String, Object>();
            map.forEach((k, v) -> out.put((String) k, replace(v, placeholders, inText)));
            return out;
        }
        if (value instanceof List<?> list) return list.stream().map(v -> replace(v, placeholders, inText)).toList();
        return value;
    }
}
