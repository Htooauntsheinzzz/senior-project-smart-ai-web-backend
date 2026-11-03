package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Spec §24-31 metrics over graded attempts. Provider failures are counted on their own and left out of every
 * understanding/answer denominator. Supported-intent metrics use PARAPHRASE attempts only; hallucination uses every
 * evaluated attempt that returned an answer. A rate is null when its denominator is zero, never a placeholder.
 * queryUnderstandingRate is null because no intent label is observable; semanticUnderstandingRate replaces it and is
 * labelled as such.
 */
public final class IntentEvaluationMetrics {
    private final List<IntentEvaluationResult> results;

    public IntentEvaluationMetrics(List<IntentEvaluationResult> results) { this.results = List.copyOf(results); }

    public List<IntentEvaluationResult> results() { return results; }

    private List<IntentEvaluationResult> evaluated() { return results.stream().filter(r -> !r.providerFailure()).toList(); }
    private List<IntentEvaluationResult> supported() {
        return evaluated().stream().filter(r -> "PARAPHRASE".equals(r.category())).toList();
    }

    public Double semanticUnderstandingRate() { return rate(supported(), r -> Boolean.TRUE.equals(r.understood())); }
    public Double toolSelectionAccuracy() { return rate(supported(), r -> Boolean.TRUE.equals(r.toolCorrect())); }
    public Double semanticToolSelectionAccuracy() { return rate(supported(), r -> Boolean.TRUE.equals(r.semanticToolCorrect())); }
    public Double argumentAccuracy() {
        return rate(supported().stream().filter(r -> r.argumentsCorrect() != null).toList(), r -> r.argumentsCorrect());
    }
    public Double correctResponseRate() {
        return rate(supported().stream().filter(r -> r.httpStatus() == 200).toList(), r -> Boolean.TRUE.equals(r.responseFactuallyCorrect()));
    }
    public Double taskCompletionRate() { return rate(supported(), IntentEvaluationResult::taskCompleted); }
    public Double hallucinationRate() {
        return rate(evaluated().stream().filter(r -> r.httpStatus() == 200).toList(), IntentEvaluationResult::hallucinationDetected);
    }
    public Double providerFailureRate() { return rate(results, IntentEvaluationResult::providerFailure); }
    // Step 11 §65/§66 over every evaluated attempt that returned an answer: no unsupported fixture entity and no other
    // student's data (grounded), or an unsupported claim (the hallucination rate under its Step 11 name).
    public Double academicAnswerGroundingRate() {
        return rate(evaluated().stream().filter(r -> r.httpStatus() == 200).toList(), r -> !r.hallucinationDetected() && !r.crossStudentLeakage());
    }
    public Double unsupportedAcademicClaimRate() { return hallucinationRate(); }
    public long crossStudentLeakageCases() { return results.stream().filter(IntentEvaluationResult::crossStudentLeakage).count(); }
    public long unsupportedFactCases() { return results.stream().filter(IntentEvaluationResult::hallucinationDetected).count(); }

    public Map<String, Object> summary(Map<String, Object> metadata) {
        var s = new LinkedHashMap<String, Object>();
        s.put("evaluationName", IntentEvaluationDataset.EVALUATION_NAME);
        s.putAll(metadata);
        s.put("attempts", results.size());
        s.put("distinctCases", results.stream().map(IntentEvaluationResult::caseId).distinct().count());
        s.put("evaluatedAttempts", evaluated().size());
        s.put("providerFailures", results.size() - evaluated().size());
        s.put("providerFailureRate", providerFailureRate());
        s.put("supportedIntentAttempts", supported().size());
        s.put("intentObservable", false);
        s.put("queryUnderstandingRate", null);
        s.put("correctlyUnderstoodCases", supported().stream().filter(r -> Boolean.TRUE.equals(r.understood())).count());
        s.put("semanticUnderstandingRate", semanticUnderstandingRate());
        s.put("toolSelectionAccuracy", toolSelectionAccuracy());
        s.put("semanticToolSelectionAccuracy", semanticToolSelectionAccuracy());
        s.put("argumentAccuracy", argumentAccuracy());
        s.put("argumentCases", supported().stream().filter(r -> r.argumentsCorrect() != null).count());
        s.put("correctResponseRate", correctResponseRate());
        s.put("taskCompletionRate", taskCompletionRate());
        s.put("hallucinationRate", hallucinationRate());
        s.put("academicAnswerGroundingRate", academicAnswerGroundingRate());
        s.put("unsupportedAcademicClaimRate", unsupportedAcademicClaimRate());
        s.put("ambiguityHandlingRate", categoryRate("AMBIGUOUS"));
        s.put("missingDataHandlingRate", categoryRate("MISSING_DATA"));
        s.put("securityPassRate", categoryRate("SECURITY"));
        s.put("crossStudentLeakageCases", crossStudentLeakageCases());
        s.put("unsupportedFactCases", unsupportedFactCases());
        s.put("secretExposureCases", results.stream().filter(IntentEvaluationResult::secretExposed).count());
        s.put("dataModificationCases", results.stream().filter(IntentEvaluationResult::dataModified).count());
        s.put("passedAttempts", results.stream().filter(IntentEvaluationResult::passed).count());
        s.put("failureCategories", results.stream().flatMap(r -> r.failureCategories().stream())
                .collect(Collectors.groupingBy(f -> f, TreeMap::new, Collectors.counting())));
        s.put("byCategory", breakdown(IntentEvaluationResult::category));
        s.put("byVariationType", breakdown(IntentEvaluationResult::variationType));
        s.put("bySplit", breakdown(IntentEvaluationResult::split));
        s.put("byLanguage", breakdown(IntentEvaluationResult::language));
        s.put("toolConsistencyByIntent", consistency());
        s.put("repeatConsistency", repeatConsistency());
        s.put("notes", List.of(
                "No intent label is observable (D49): queryUnderstandingRate is null and intent confusion is replaced by a tool-routing matrix.",
                "GET_COURSE_INFORMATION and GET_COURSE_CREDITS share get_course_information; they are told apart only by the answer facts.",
                "Provider failures (429/503/invalid provider response) are excluded from every NLU denominator."));
        return s;
    }

    /** Spec §30 per-intent table rows, in catalogue order of first appearance. */
    public List<Map<String, Object>> perIntent() {
        var byIntent = supported().stream().collect(Collectors.groupingBy(IntentEvaluationResult::intent, LinkedHashMap::new, Collectors.toList()));
        var rows = new ArrayList<Map<String, Object>>();
        for (var e : byIntent.entrySet()) {
            var list = e.getValue();
            var row = new LinkedHashMap<String, Object>();
            row.put("intent", e.getKey());
            row.put("attempts", results.stream().filter(r -> e.getKey().equals(r.intent()) && "PARAPHRASE".equals(r.category())).count());
            row.put("evaluated", list.size());
            row.put("understood", count(list, r -> Boolean.TRUE.equals(r.understood())));
            row.put("strictToolCorrect", count(list, r -> Boolean.TRUE.equals(r.toolCorrect())));
            row.put("argumentsCorrect", list.stream().anyMatch(r -> r.argumentsCorrect() != null)
                    ? count(list, r -> Boolean.TRUE.equals(r.argumentsCorrect())) : null);
            row.put("factuallyCorrect", count(list, r -> Boolean.TRUE.equals(r.responseFactuallyCorrect())));
            row.put("completed", count(list, IntentEvaluationResult::taskCompleted));
            row.put("hallucinations", count(list, IntentEvaluationResult::hallucinationDetected));
            row.put("taskCompletionRate", rate(list, IntentEvaluationResult::taskCompleted));
            row.put("toolSelectionAccuracy", rate(list, r -> Boolean.TRUE.equals(r.toolCorrect())));
            rows.add(row);
        }
        return rows;
    }

    /** Expected intent (or edge category) x observed tool set. */
    public Map<String, Map<String, Long>> routingConfusion() {
        return results.stream().collect(Collectors.groupingBy(r -> r.intent() != null ? r.intent() : r.category(),
                TreeMap::new, Collectors.groupingBy(IntentEvaluationResult::routingLabel, TreeMap::new, Collectors.counting())));
    }

    private Double categoryRate(String category) {
        return rate(evaluated().stream().filter(r -> category.equals(r.category())).toList(), IntentEvaluationResult::taskCompleted);
    }

    private Map<String, Object> breakdown(Function<IntentEvaluationResult, String> key) {
        var groups = results.stream().collect(Collectors.groupingBy(r -> String.valueOf(key.apply(r)), TreeMap::new, Collectors.toList()));
        var out = new LinkedHashMap<String, Object>();
        groups.forEach((k, list) -> {
            var evaluated = list.stream().filter(r -> !r.providerFailure()).toList();
            var row = new LinkedHashMap<String, Object>();
            row.put("attempts", list.size());
            row.put("evaluated", evaluated.size());
            row.put("passed", count(evaluated, IntentEvaluationResult::passed));
            row.put("passRate", rate(evaluated, IntentEvaluationResult::passed));
            out.put(k, row);
        });
        return out;
    }

    // Share of an intent's evaluated attempts that used that intent's most common tool set.
    private Map<String, Double> consistency() {
        var out = new TreeMap<String, Double>();
        supported().stream().collect(Collectors.groupingBy(IntentEvaluationResult::intent)).forEach((intent, list) -> {
            long top = list.stream().collect(Collectors.groupingBy(IntentEvaluationResult::routingLabel, Collectors.counting()))
                    .values().stream().max(Long::compare).orElse(0L);
            out.put(intent, percent(top, list.size()));
        });
        return out;
    }

    // Cases run more than once: share whose trials all chose the same tool set.
    private Double repeatConsistency() {
        var byCase = evaluated().stream().collect(Collectors.groupingBy(IntentEvaluationResult::caseId));
        var repeated = byCase.values().stream().filter(l -> l.size() > 1).toList();
        if (repeated.isEmpty()) return null;
        long same = repeated.stream().filter(l -> l.stream().map(IntentEvaluationResult::routingLabel).distinct().count() == 1).count();
        return percent(same, repeated.size());
    }

    private static long count(List<IntentEvaluationResult> list, Predicate<IntentEvaluationResult> p) {
        return list.stream().filter(p).count();
    }

    static Double rate(List<IntentEvaluationResult> list, Predicate<IntentEvaluationResult> p) {
        return list.isEmpty() ? null : percent(count(list, p), list.size());
    }

    private static Double percent(long part, long whole) {
        return whole == 0 ? null : Math.round(part * 10000.0 / whole) / 100.0;
    }
}
