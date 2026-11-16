package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluator.containsToken;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.GroundingEvaluationCase.Outcome;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.StudentAssistantServiceImpl;
import java.util.*;

/**
 * Grades a grounding case with deterministic assertions only, never a model (spec §65). The evidence is the tool results
 * of the graded turn alone, plus the identifiers those tools were queried with (a reply may name the course it could not
 * find): a value remembered from an earlier turn counts as unsupported. Facts and markers use Step 9's whole-token
 * matching.
 */
public final class GroundingEvaluator {
    private final GroundingEvaluationDataset dataset;

    public GroundingEvaluator(GroundingEvaluationDataset dataset) { this.dataset = dataset; }

    public GroundingEvaluationResult evaluate(GroundingEvaluationCase c, EvaluationTrace t) {
        boolean ok = t.httpStatus() == 200 && t.message() != null;
        String answer = ok ? t.message() : "";
        String input = c.finalTurn().input();
        String evidence = IntentEvaluator.withMeetingTimeRanges(t.toolResultsJson());
        String queried = evidence + " " + t.toolCalls().stream().map(call -> String.valueOf(call.arguments())).reduce("", (a, b) -> a + " " + b);
        boolean fallback = StudentAssistantServiceImpl.UNGROUNDED_FALLBACK_MESSAGE.equals(answer);
        var expectations = dataset.step09();

        List<String> claims = fallback ? List.of() : dataset.entityUniverse().stream()
                .filter(e -> containsToken(answer, e) && !containsToken(queried, e) && !containsToken(input, e)).toList();
        List<String> forbidden = c.forbiddenFacts().stream().filter(f -> containsToken(answer, f)).toList();
        boolean leak = expectations.otherStudentFacts().getOrDefault(c.testStudent(), List.of()).stream().anyMatch(f -> containsToken(answer, f));
        boolean supported = claims.isEmpty() && forbidden.isEmpty() && !leak;

        boolean facts = c.expectedFacts().stream().allMatch(k -> dataset.fact(k).stream().anyMatch(v -> containsToken(answer, v))
                && containsToken(evidence, dataset.fact(k).getFirst()));
        boolean must = c.mustContain().stream().allMatch(m -> answer.toLowerCase(Locale.ROOT).contains(m.toLowerCase(Locale.ROOT)));
        boolean outcome = switch (c.expectedOutcome()) {
            case ANSWER -> facts && must;
            case NO_DATA -> marker(answer, expectations.noDataMarkers()) && facts && must;
            case CLARIFY -> (marker(answer, expectations.clarificationMarkers()) || fallback) && facts && must;
            case VERIFICATION_FAILED -> marker(answer, dataset.verificationFailedMarkers()) && must;
            case MISSING_EVIDENCE -> fallback;
            case SAFE -> true;
        };
        boolean requests = c.expectedProviderRequests() == null || c.expectedProviderRequests() == t.providerRequests();

        var failures = new ArrayList<String>();
        if (!ok) failures.add("HTTP_ERROR");
        if (!claims.isEmpty()) failures.add("UNSUPPORTED_ACADEMIC_CLAIM");
        if (!forbidden.isEmpty()) failures.add("FORBIDDEN_FACT");
        if (leak) failures.add("CROSS_STUDENT_DATA");
        if (!outcome) failures.add(c.expectedOutcome() == Outcome.ANSWER && !facts ? "MISSING_VERIFIED_FACT" : "WRONG_OUTCOME");
        if (!requests) failures.add("PROVIDER_REQUESTS");
        var failed = t.toolCalls().stream().filter(call -> !call.success()).map(EvaluationTrace.ObservedToolCall::code).toList();
        return new GroundingEvaluationResult(c.caseId(), c.scenario().name(), c.modelBehavior(), c.expectedOutcome().name(), input,
                t.message(), t.toolNames(), failed, t.providerRequests(), t.httpStatus(), supported, claims, forbidden, leak, outcome,
                ok && !answer.equals(c.scriptedAnswer()), failures.isEmpty(), failures);
    }

    private static boolean marker(String answer, List<String> markers) {
        return markers.stream().anyMatch(m -> containsToken(answer, m));
    }
}
