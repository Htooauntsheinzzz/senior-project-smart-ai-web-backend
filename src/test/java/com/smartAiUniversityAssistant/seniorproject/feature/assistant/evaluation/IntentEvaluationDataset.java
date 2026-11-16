package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * The versioned Step 9 dataset under src/test/resources/assistant/step09/: intent-variations.jsonl (paraphrases of the
 * ten intents), ambiguous-questions.jsonl, negative-cases.jsonl (missing data and security) and
 * fixture-expectations.json (the ground-truth facts of the synthetic fixture and the grading vocabulary).
 */
public final class IntentEvaluationDataset {
    public static final String EVALUATION_NAME = "STEP_09_NATURAL_LANGUAGE_VARIATIONS";
    static final List<String> CASE_FILES = List.of("intent-variations.jsonl", "ambiguous-questions.jsonl", "negative-cases.jsonl");
    private static final String ROOT = "/assistant/step09/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final IntentEvaluationDataset INSTANCE = load();

    private final List<IntentEvaluationCase> cases;
    private final FixtureExpectations expectations;

    private IntentEvaluationDataset(List<IntentEvaluationCase> cases, FixtureExpectations expectations) {
        this.cases = List.copyOf(cases);
        this.expectations = expectations;
    }

    public static IntentEvaluationDataset get() { return INSTANCE; }

    public List<IntentEvaluationCase> cases() { return cases; }
    public FixtureExpectations expectations() { return expectations; }
    public String version() { return expectations.datasetVersion(); }

    public List<IntentEvaluationCase> cases(IntentEvaluationCase.Category category) {
        return cases.stream().filter(c -> c.category() == category).toList();
    }

    /** Raw lines of one file, for structure checks. */
    static List<Map<String, Object>> rawLines(String file) { return readLines(file); }

    private static IntentEvaluationDataset load() {
        var cases = new ArrayList<IntentEvaluationCase>();
        for (String file : CASE_FILES) readLines(file).forEach(m -> cases.add(IntentEvaluationCase.fromMap(m)));
        try (InputStream in = IntentEvaluationDataset.class.getResourceAsStream(ROOT + "fixture-expectations.json")) {
            return new IntentEvaluationDataset(cases, FixtureExpectations.fromMap(JSON.readValue(in, Map.class)));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readLines(String file) {
        try (InputStream in = Objects.requireNonNull(IntentEvaluationDataset.class.getResourceAsStream(ROOT + file), file);
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            var lines = new ArrayList<Map<String, Object>>();
            String line;
            while ((line = reader.readLine()) != null) if (!line.isBlank()) lines.add(JSON.readValue(line, Map.class));
            return lines;
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /**
     * Ground truth and grading vocabulary. facts: fact key -> accepted spellings (any one must appear in the answer;
     * the first, canonical one must also appear in the tool results, which is the independent check against
     * PostgreSQL). entityUniverse: every room, lecturer, course, schedule and program name in the fixture; one in an
     * answer that is in neither the tool results nor the question is an unsupported claim. otherStudentFacts: data
     * that only another student may see, per test student.
     */
    public record FixtureExpectations(String datasetVersion, String fixtureVersion, Map<String, List<String>> facts,
            List<String> entityUniverse, Map<String, List<String>> otherStudentFacts, List<String> clarificationMarkers,
            List<String> noDataMarkers, List<String> secretMarkers) {

        @SuppressWarnings("unchecked")
        static FixtureExpectations fromMap(Map<String, Object> m) {
            return new FixtureExpectations((String) m.get("datasetVersion"), (String) m.get("fixtureVersion"),
                    (Map<String, List<String>>) m.get("facts"), (List<String>) m.get("entityUniverse"),
                    (Map<String, List<String>>) m.get("otherStudentFacts"), (List<String>) m.get("clarificationMarkers"),
                    (List<String>) m.get("noDataMarkers"), (List<String>) m.get("secretMarkers"));
        }

        public List<String> fact(String key) {
            return Objects.requireNonNull(facts.get(key), () -> "Unknown fact key " + key);
        }
    }
}
