package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.GroundingEvaluationCase.Scenario;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluationDataset.FixtureExpectations;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Step 11 grounding dataset under src/test/resources/assistant/step11/, graded with the Step 9 fixture
 * expectations (facts, entity universe, other-student facts, no-data and clarification markers) plus the additions in
 * grounding-expectations.json. Reuses the Step 9 framework instead of a separate one (spec §64).
 */
public final class GroundingEvaluationDataset {
    public static final String EVALUATION_NAME = "STEP_11_AI_GROUNDING_AND_ACADEMIC_SAFETY";
    private static final String ROOT = "/assistant/step11/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final GroundingEvaluationDataset INSTANCE = load();

    private final List<GroundingEvaluationCase> cases;
    private final FixtureExpectations step09;
    private final Map<String, List<String>> facts;
    private final List<String> entityUniverse;
    private final List<String> verificationFailedMarkers;
    private final Map<Scenario, Integer> minimumCases;
    private final String version;

    private GroundingEvaluationDataset(List<GroundingEvaluationCase> cases, FixtureExpectations step09, Map<String, Object> extra) {
        this.cases = List.copyOf(cases);
        this.step09 = step09;
        var merged = new LinkedHashMap<>(step09.facts());
        merged.putAll(cast(extra.get("facts")));
        this.facts = Collections.unmodifiableMap(merged);
        var universe = new ArrayList<>(step09.entityUniverse());
        universe.addAll(cast(extra.get("extraEntities")));
        this.entityUniverse = List.copyOf(universe);
        this.verificationFailedMarkers = List.copyOf(cast(extra.get("verificationFailedMarkers")));
        var minimum = new EnumMap<Scenario, Integer>(Scenario.class);
        Map<String, Integer> raw = cast(extra.get("minimumCases"));
        raw.forEach((k, v) -> minimum.put(Scenario.valueOf(k), v));
        this.minimumCases = Collections.unmodifiableMap(minimum);
        this.version = (String) extra.get("datasetVersion");
    }

    public static GroundingEvaluationDataset get() { return INSTANCE; }

    public List<GroundingEvaluationCase> cases() { return cases; }
    public FixtureExpectations step09() { return step09; }
    public List<String> entityUniverse() { return entityUniverse; }
    public List<String> verificationFailedMarkers() { return verificationFailedMarkers; }
    public Map<Scenario, Integer> minimumCases() { return minimumCases; }
    public String version() { return version; }
    public boolean knowsFact(String key) { return facts.containsKey(key); }

    public List<String> fact(String key) {
        return Objects.requireNonNull(facts.get(key), () -> "Unknown fact key " + key);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) { return (T) value; }

    @SuppressWarnings("unchecked")
    private static GroundingEvaluationDataset load() {
        var cases = new ArrayList<GroundingEvaluationCase>();
        try (InputStream in = Objects.requireNonNull(GroundingEvaluationDataset.class.getResourceAsStream(ROOT + "grounding-cases.jsonl"));
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) if (!line.isBlank()) cases.add(GroundingEvaluationCase.fromMap(JSON.readValue(line, Map.class)));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        try (InputStream in = GroundingEvaluationDataset.class.getResourceAsStream(ROOT + "grounding-expectations.json")) {
            return new GroundingEvaluationDataset(cases, IntentEvaluationDataset.get().expectations(), JSON.readValue(in, Map.class));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
