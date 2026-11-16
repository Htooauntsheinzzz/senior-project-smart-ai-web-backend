package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Spec §64-67 grounding metrics over graded cases. A rate is null when its denominator is zero. All cases are academic
 * questions, so the grounding and unsupported-claim rates use every case; the scenario accuracies use that scenario's
 * cases. These are deterministic-mode results (scripted model), not a measurement of Gemini.
 */
public final class GroundingEvaluationMetrics {
    private final List<GroundingEvaluationResult> results;

    public GroundingEvaluationMetrics(List<GroundingEvaluationResult> results) { this.results = List.copyOf(results); }

    public List<GroundingEvaluationResult> results() { return results; }

    public Double academicAnswerGroundingRate() { return rate(results, GroundingEvaluationResult::supported); }
    public Double unsupportedAcademicClaimRate() { return rate(results, r -> !r.supported()); }
    public Double missingDataHandlingAccuracy() { return scenario("MISSING_DATA"); }
    public Double toolFailureHandlingAccuracy() { return scenario("TOOL_FAILURE"); }
    public Double multiFactAnswerAccuracy() { return scenario("MULTI_FACT"); }
    public Double conversationFreshnessAccuracy() { return scenario("CONVERSATION_FRESHNESS"); }

    public Map<String, Object> summary(Map<String, Object> metadata) {
        var s = new LinkedHashMap<String, Object>();
        s.put("evaluationName", GroundingEvaluationDataset.EVALUATION_NAME);
        s.putAll(metadata);
        s.put("cases", results.size());
        s.put("passedCases", results.stream().filter(GroundingEvaluationResult::passed).count());
        s.put("academicAnswerGroundingRate", academicAnswerGroundingRate());
        s.put("unsupportedAcademicClaimRate", unsupportedAcademicClaimRate());
        s.put("missingDataHandlingAccuracy", missingDataHandlingAccuracy());
        s.put("toolFailureHandlingAccuracy", toolFailureHandlingAccuracy());
        s.put("multiFactAnswerAccuracy", multiFactAnswerAccuracy());
        s.put("conversationFreshnessAccuracy", conversationFreshnessAccuracy());
        s.put("verifiedFactAccuracy", scenario("VERIFIED_FACTS"));
        s.put("ambiguityHandlingAccuracy", scenario("AMBIGUOUS"));
        s.put("promptInjectionPassRate", scenario("PROMPT_INJECTION"));
        var faulty = results.stream().filter(r -> "FAULTY".equals(r.modelBehavior())).toList();
        s.put("faultyModelCases", faulty.size());
        s.put("faultyModelAnswersReplacedRate", rate(faulty, GroundingEvaluationResult::backendReplaced));
        s.put("crossStudentDataCases", results.stream().filter(GroundingEvaluationResult::crossStudentData).count());
        s.put("failureCategories", results.stream().flatMap(r -> r.failures().stream())
                .collect(Collectors.groupingBy(f -> f, TreeMap::new, Collectors.counting())));
        var byScenario = new TreeMap<String, Object>();
        results.stream().collect(Collectors.groupingBy(GroundingEvaluationResult::scenario)).forEach((k, list) -> {
            var row = new LinkedHashMap<String, Object>();
            row.put("cases", list.size());
            row.put("passed", list.stream().filter(GroundingEvaluationResult::passed).count());
            row.put("passRate", rate(list, GroundingEvaluationResult::passed));
            row.put("faultyModelCases", list.stream().filter(r -> "FAULTY".equals(r.modelBehavior())).count());
            byScenario.put(k, row);
        });
        s.put("byScenario", byScenario);
        s.put("notes", List.of(
                "Deterministic mode: Gemini is replaced by each case's script, honest or deliberately faulty; the rates measure the backend grounding layer, not Gemini.",
                "Evidence is the graded turn's own tool results; Redis history is never evidence.",
                "0 unsupported academic claims is a testing target for this dataset, not a guarantee for future model output."));
        return s;
    }

    private Double scenario(String scenario) {
        return rate(results.stream().filter(r -> scenario.equals(r.scenario())).toList(), GroundingEvaluationResult::passed);
    }

    private static Double rate(List<GroundingEvaluationResult> list, Predicate<GroundingEvaluationResult> p) {
        if (list.isEmpty()) return null;
        return Math.round(list.stream().filter(p).count() * 10000.0 / list.size()) / 100.0;
    }
}
