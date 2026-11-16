package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import java.io.InputStream;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** The versioned Step 8 regression dataset (src/test/resources/assistant/step8-intent-regression.json). */
final class IntentRegressionDataset {
    private static final Map<String, Object> DATASET = load();

    private IntentRegressionDataset() {
    }

    /** "positive" or "negative" cases, as read from the JSON file. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> cases(String kind) { return (List<Map<String, Object>>) DATASET.get(kind); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load() {
        try (InputStream in = IntentRegressionDataset.class.getResourceAsStream("/assistant/step8-intent-regression.json")) {
            return JsonMapper.builder().build().readValue(in, Map.class);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
