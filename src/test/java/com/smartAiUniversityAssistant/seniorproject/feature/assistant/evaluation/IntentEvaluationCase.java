package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;

/**
 * One Step 9 evaluation case, as one JSONL line. The labels (intent, expected tools, arguments, fact keys) are
 * evaluation annotations only: they are never sent with the chat request. expectedFacts are keys into
 * fixture-expectations.json; forbiddenFacts are literal strings that must not appear in the answer. script is the
 * stand-in model for the deterministic mode and is ignored by the live run. Values such as ${CS301_SEC01} are fixture
 * placeholders, resolved per run by {@link #resolve(Map)}.
 */
public record IntentEvaluationCase(String caseId, Category category, String intent, String input, String variationType,
        String language, Split split, String testStudent, Behavior expectedBehavior, String expectedPrimaryTool,
        List<String> expectedTools, List<List<String>> acceptableToolSets, Map<String, Object> expectedArguments,
        List<String> expectedFacts, List<String> forbiddenFacts, List<String> expectedToolCodes,
        List<Map<String, Object>> script) {

    public enum Category { PARAPHRASE, AMBIGUOUS, MISSING_DATA, SECURITY }

    public enum Split { DEV, HOLDOUT }

    /** ANSWER with facts, NO_DATA (say the record is missing), CLARIFY (ask instead of guessing), REFUSE (no access). */
    public enum Behavior { ANSWER, NO_DATA, CLARIFY, REFUSE }

    public IntentEvaluationCase {
        expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
        acceptableToolSets = acceptableToolSets == null ? List.of() : List.copyOf(acceptableToolSets);
        expectedArguments = expectedArguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(expectedArguments));
        expectedFacts = expectedFacts == null ? List.of() : List.copyOf(expectedFacts);
        forbiddenFacts = forbiddenFacts == null ? List.of() : List.copyOf(forbiddenFacts);
        expectedToolCodes = expectedToolCodes == null ? List.of() : List.copyOf(expectedToolCodes);
        script = script == null ? List.of() : List.copyOf(script);
    }

    @SuppressWarnings("unchecked")
    static IntentEvaluationCase fromMap(Map<String, Object> m) {
        String primary = (String) m.get("expectedPrimaryTool");
        List<String> tools = (List<String>) m.get("expectedTools");
        if (tools == null) tools = primary == null ? List.of() : List.of(primary);
        return new IntentEvaluationCase((String) m.get("caseId"), Category.valueOf((String) m.get("category")),
                (String) m.get("intent"), (String) m.get("input"), (String) m.get("variationType"),
                (String) m.getOrDefault("language", "en"), Split.valueOf((String) m.getOrDefault("split", "DEV")),
                (String) m.getOrDefault("testStudent", "STD-TEST-001"), Behavior.valueOf((String) m.get("expectedBehavior")),
                primary, tools, (List<List<String>>) m.get("acceptableToolSets"),
                (Map<String, Object>) m.get("expectedArguments"), (List<String>) m.get("expectedFacts"),
                (List<String>) m.get("forbiddenFacts"), (List<String>) m.get("expectedToolCodes"),
                (List<Map<String, Object>>) m.get("script"));
    }

    /** The tool calls an ideal model makes when no script is given: the primary tool with the expected arguments. */
    public List<Map<String, Object>> effectiveScript() {
        if (!script.isEmpty() || expectedPrimaryTool == null) return script;
        return List.of(Map.of("tools", List.of(Map.of("name", expectedPrimaryTool, "arguments", expectedArguments))));
    }

    /** A copy with every ${NAME} placeholder replaced by the fixture's generated value. */
    @SuppressWarnings("unchecked")
    public IntentEvaluationCase resolve(Map<String, Object> placeholders) {
        return new IntentEvaluationCase(caseId, category, intent, (String) replace(input, placeholders, true), variationType,
                language, split, testStudent, expectedBehavior, expectedPrimaryTool, expectedTools, acceptableToolSets,
                (Map<String, Object>) replace(expectedArguments, placeholders, false), expectedFacts, forbiddenFacts,
                expectedToolCodes, (List<Map<String, Object>>) replace(script, placeholders, false));
    }

    public boolean supportedIntent() { return category == Category.PARAPHRASE; }

    // ${FROM_PREVIOUS} is left for the scripted model, which reads it from the previous tool result.
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
