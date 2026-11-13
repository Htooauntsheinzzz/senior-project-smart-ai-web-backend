package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.CurriculumEvaluationCase.Category;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** The Step 15 curriculum dataset under src/test/resources/assistant/step15/ (spec file 55, section 11). */
public final class CurriculumEvaluationDataset {
    private static final String ROOT = "/assistant/step15/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final CurriculumEvaluationDataset INSTANCE = load();

    private final List<CurriculumEvaluationCase> cases;
    private final Map<String, Object> expectations;

    private CurriculumEvaluationDataset(List<CurriculumEvaluationCase> cases, Map<String, Object> expectations) {
        this.cases = List.copyOf(cases);
        this.expectations = expectations;
    }

    public static CurriculumEvaluationDataset get() { return INSTANCE; }

    public List<CurriculumEvaluationCase> cases() { return cases; }
    public String version() { return (String) expectations.get("datasetVersion"); }
    public String regulationsTitle() { return documents().get("regulations"); }
    public String curriculumTitle() { return documents().get("curriculum"); }
    public int minimumTotal() { return (Integer) expectations.get("minimumTotal"); }
    @SuppressWarnings("unchecked") public List<String> otherProgramMarkers() { return (List<String>) expectations.get("otherProgramMarkers"); }
    @SuppressWarnings("unchecked") public List<String> fixtureValues() { return (List<String>) expectations.get("fixtureValues"); }

    @SuppressWarnings("unchecked")
    public Map<Category, Integer> minimumCases() {
        var out = new EnumMap<Category, Integer>(Category.class);
        ((Map<String, Integer>) expectations.get("minimumCases")).forEach((k, v) -> out.put(Category.valueOf(k), v));
        return out;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> documents() { return (Map<String, String>) expectations.get("documents"); }

    @SuppressWarnings("unchecked")
    private static CurriculumEvaluationDataset load() {
        var cases = new ArrayList<CurriculumEvaluationCase>();
        try (InputStream in = Objects.requireNonNull(CurriculumEvaluationDataset.class.getResourceAsStream(ROOT + "curriculum-cases.jsonl"));
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) if (!line.isBlank()) cases.add(CurriculumEvaluationCase.fromMap(JSON.readValue(line, Map.class)));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        try (InputStream in = CurriculumEvaluationDataset.class.getResourceAsStream(ROOT + "curriculum-expectations.json")) {
            return new CurriculumEvaluationDataset(cases, JSON.readValue(in, Map.class));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
