package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluator.containsToken;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import java.util.*;

/** Spec file 55, section 11.5: deterministic grading of one Step 15 case. Never asks a model. */
final class CurriculumEvaluator {
    static final String PASSAGE_INTRO = "Here is what the university documents say:";
    private final CurriculumEvaluationDataset dataset;

    CurriculumEvaluator(CurriculumEvaluationDataset dataset) { this.dataset = dataset; }

    /** The graded result of one case; failures is empty when the case passed. */
    record Result(String caseId, String category, String expectedOutcome, boolean passed, List<String> failures, String message,
            List<String> sources, List<String> tools, int providerRequests, boolean knownLimit, boolean faultyReplaced,
            boolean crossProgramFinding) {}

    Result evaluate(CurriculumEvaluationCase c, EvaluationTrace t, List<String> sources) {
        var failures = new ArrayList<String>();
        String message = t.message() == null ? "" : t.message();
        if (t.httpStatus() != 200 || t.message() == null) failures.add("HTTP_" + t.httpStatus());
        boolean outcome = switch (c.expectedOutcome()) {
            case ANSWER -> message.equals(c.lastText() == null ? null : c.lastText().strip());
            case PASSAGES -> message.contains(PASSAGE_INTRO);
            case NOT_FOUND -> message.equals(SafeAcademicResponseFactoryImpl.KNOWLEDGE_NOT_FOUND);
            case MISSING_EVIDENCE -> message.endsWith(SafeAcademicResponseFactoryImpl.KNOWLEDGE_MISSING_EVIDENCE);
        };
        if (!outcome) failures.add("OUTCOME_" + c.expectedOutcome());
        for (String value : c.mustContain()) if (!containsToken(message, value)) failures.add("MISSING:" + value);
        for (String value : c.forbiddenFacts()) if (containsToken(message, value)) failures.add("FORBIDDEN:" + value);
        if (c.expectedSources() != null && !c.expectedSources().equals(sources)) failures.add("SOURCES");
        if (c.expectedTools() != null && !c.expectedTools().equals(t.toolNames())) failures.add("TOOLS");
        if (c.expectedProviderRequests() != null && c.expectedProviderRequests() != t.providerRequests()) failures.add("PROVIDER_REQUESTS");
        boolean cross = false;
        if (!"STD-TEST-001".equals(c.testStudent()))
            for (String marker : dataset.otherProgramMarkers())
                if (containsToken(message, marker) || containsToken(t.toolResultsJson(), marker)
                        || sources.stream().anyMatch(s -> s.contains(marker))) cross = true;
        if (cross) failures.add("CROSS_PROGRAM_DATA");
        boolean replaced = "FAULTY".equals(c.modelBehavior()) && c.lastText() != null && !message.equals(c.lastText().strip());
        return new Result(c.caseId(), c.category().name(), c.expectedOutcome().name(), failures.isEmpty(), List.copyOf(failures), message,
                sources, t.toolNames(), t.providerRequests(), c.knownLimit(), replaced, cross);
    }
}
