package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes spec §41's report files into one directory: summary.json, intent-accuracy.csv, case-results.jsonl,
 * confusion-matrix.csv (tool routing, since no intent label is observable) and failures.jsonl. Local test artifacts:
 * they hold the synthetic fixture's answers only, never tokens, passwords or the API key (redacted by the evaluator).
 */
public final class IntentEvaluationReportWriter {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private IntentEvaluationReportWriter() {
    }

    public static Map<String, Object> write(Path directory, IntentEvaluationMetrics metrics, Map<String, Object> metadata)
            throws IOException {
        Files.createDirectories(directory);
        Map<String, Object> summary = metrics.summary(metadata);
        summary.put("perIntent", metrics.perIntent());
        Files.writeString(directory.resolve("summary.json"), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(summary));

        var csv = new StringBuilder("intent,attempts,evaluated,understood,strictToolCorrect,argumentsCorrect,factuallyCorrect,"
                + "completed,hallucinations,toolSelectionAccuracy,taskCompletionRate\n");
        for (var row : metrics.perIntent())
            csv.append(String.join(",", List.of("intent", "attempts", "evaluated", "understood", "strictToolCorrect",
                    "argumentsCorrect", "factuallyCorrect", "completed", "hallucinations", "toolSelectionAccuracy",
                    "taskCompletionRate").stream().map(k -> row.get(k) == null ? "" : String.valueOf(row.get(k))).toList())).append('\n');
        Files.writeString(directory.resolve("intent-accuracy.csv"), csv);

        var cases = new StringBuilder();
        var failures = new StringBuilder();
        for (IntentEvaluationResult r : metrics.results()) {
            String line = JSON.writeValueAsString(r) + "\n";
            cases.append(line);
            if (!r.passed()) failures.append(line);
        }
        Files.writeString(directory.resolve("case-results.jsonl"), cases);
        Files.writeString(directory.resolve("failures.jsonl"), failures);

        var confusion = metrics.routingConfusion();
        var labels = new TreeSet<String>();
        confusion.values().forEach(m -> labels.addAll(m.keySet()));
        var matrix = new StringBuilder("expected").append(labels.stream().map(l -> "," + csvCell(l)).reduce("", String::concat)).append('\n');
        confusion.forEach((expected, row) -> {
            matrix.append(csvCell(expected));
            labels.forEach(l -> matrix.append(',').append(row.getOrDefault(l, 0L)));
            matrix.append('\n');
        });
        Files.writeString(directory.resolve("confusion-matrix.csv"), matrix);
        return summary;
    }

    private static String csvCell(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
