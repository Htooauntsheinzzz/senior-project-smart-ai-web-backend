package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.HybridEvaluationCase.Category;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** The Step 16 hybrid dataset under src/test/resources/assistant/step16/ (spec file 57, section 16). */
public final class HybridEvaluationDataset {
    private static final String ROOT = "/assistant/step16/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HybridEvaluationDataset INSTANCE = load();

    private final List<HybridEvaluationCase> cases;
    private final Map<String, Object> expectations;

    private HybridEvaluationDataset(List<HybridEvaluationCase> cases, Map<String, Object> expectations) {
        this.cases = List.copyOf(cases);
        this.expectations = expectations;
    }

    public static HybridEvaluationDataset get() { return INSTANCE; }

    public List<HybridEvaluationCase> cases() { return cases; }
    public String version() { return (String) expectations.get("datasetVersion"); }
    public String regulationsTitle() { return documents().get("regulations"); }
    public String curriculumTitle() { return documents().get("curriculum"); }
    public int minimumTotal() { return (Integer) expectations.get("minimumTotal"); }
    @SuppressWarnings("unchecked") public List<String> otherProgramMarkers() { return (List<String>) expectations.get("otherProgramMarkers"); }
    @SuppressWarnings("unchecked") public List<String> otherStudentMarkers() { return (List<String>) expectations.get("otherStudentMarkers"); }

    @SuppressWarnings("unchecked")
    public Map<Category, Integer> minimumCases() {
        var out = new EnumMap<Category, Integer>(Category.class);
        ((Map<String, Integer>) expectations.get("minimumCases")).forEach((k, v) -> out.put(Category.valueOf(k), v));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> documents() { return (Map<String, String>) expectations.get("documents"); }

    @SuppressWarnings("unchecked")
    private static HybridEvaluationDataset load() {
        var cases = new ArrayList<HybridEvaluationCase>();
        try (InputStream in = Objects.requireNonNull(HybridEvaluationDataset.class.getResourceAsStream(ROOT + "hybrid-cases.jsonl"));
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) if (!line.isBlank()) cases.add(HybridEvaluationCase.fromMap(JSON.readValue(line, Map.class)));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        try (InputStream in = HybridEvaluationDataset.class.getResourceAsStream(ROOT + "hybrid-expectations.json")) {
            return new HybridEvaluationDataset(cases, JSON.readValue(in, Map.class));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
