package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluator.containsToken;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.HybridEvaluationCase.Category;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import java.util.*;

/** Spec file 57, section 16.5: deterministic grading of one Step 16 case. Never asks a model. */
final class HybridEvaluator {
    static final String PROGRESS_INTRO = "Your academic progress:";
    private final HybridEvaluationDataset dataset;

    HybridEvaluator(HybridEvaluationDataset dataset) { this.dataset = dataset; }

    record Result(String caseId, String category, String expectedOutcome, boolean passed, List<String> failures, String message,
            List<String> sources, List<String> tools, int providerRequests, boolean knownLimit, boolean faultyReplaced,
            boolean crossProgramFinding, boolean crossStudentFinding, boolean identityFinding) {}

    Result evaluate(HybridEvaluationCase c, EvaluationTrace t, List<String> sources, List<LlmRequest> requests, String studentEmail) {
        var failures = new ArrayList<String>();
        String message = t.message() == null ? "" : t.message();
        // Self-check of the dataset: a forbidden number must not be a fact of the request.
        for (String value : c.forbiddenFacts())
            if (value.matches("\\d+(\\.\\d+)?") && containsToken(t.toolResultsJson(), value)) failures.add("BROKEN_CASE: the wrong value is a fact: " + value);
        if (t.httpStatus() != 200 || t.message() == null) failures.add("HTTP_" + t.httpStatus());
        var responses = new SafeAcademicResponseFactoryImpl();
        boolean outcome = switch (c.expectedOutcome()) {
            case ANSWER -> message.equals(c.lastText() == null ? null : c.lastText().strip());
            case CHECKED_REPLY -> message.contains(PROGRESS_INTRO) && message.contains(CurriculumEvaluator.PASSAGE_INTRO)
                    && message.endsWith(SafeAcademicResponseFactoryImpl.DEGREE_AUDIT_REFERRAL);
            case DOCUMENT_NOT_FOUND -> message.startsWith(PROGRESS_INTRO) && message.endsWith(SafeAcademicResponseFactoryImpl.KNOWLEDGE_NOT_FOUND);
            case MISSING_EVIDENCE -> message.endsWith(SafeAcademicResponseFactoryImpl.KNOWLEDGE_MISSING_EVIDENCE);
            case SERVICE_UNAVAILABLE -> message.equals(responses.academicServiceUnavailable());
        };
        if (!outcome) failures.add("OUTCOME_" + c.expectedOutcome());
        for (String value : c.mustContain()) if (!containsToken(message, value)) failures.add("MISSING:" + value);
        for (String value : c.forbiddenFacts()) if (containsToken(message, value)) failures.add("FORBIDDEN:" + value);
        if (c.expectedSources() != null && !c.expectedSources().equals(sources)) failures.add("SOURCES");
        if (c.expectedTools() != null && !c.expectedTools().equals(t.toolNames())) failures.add("TOOLS");
        if (c.expectedProviderRequests() != null && c.expectedProviderRequests() != t.providerRequests()) failures.add("PROVIDER_REQUESTS");
        // The backend's progress call comes first (round 0) for every hybrid case, and never for NOT_HYBRID.
        boolean backendCall = !t.toolCalls().isEmpty() && t.toolCalls().getFirst().round() == 0
                && "get_my_academic_progress".equals(t.toolCalls().getFirst().name());
        if (backendCall != (c.category() != Category.NOT_HYBRID)) failures.add("BACKEND_PROGRESS_CALL");
        boolean cross = false, student = false;
        if (!"STD-TEST-001".equals(c.testStudent())) {
            for (String marker : dataset.otherProgramMarkers())
                if (containsToken(message, marker) || containsToken(t.toolResultsJson(), marker)) cross = true;
            for (String marker : dataset.otherStudentMarkers())
                if (containsToken(message, marker) || containsToken(t.toolResultsJson(), marker)
                        || requests.stream().anyMatch(r -> r.systemPrompt().contains(marker))) student = true;
        }
        boolean identity = requests.stream().map(Object::toString)
                .anyMatch(r -> r.contains(c.testStudent()) || r.toLowerCase(Locale.ROOT).contains(studentEmail));
        if (cross) failures.add("CROSS_PROGRAM_DATA");
        if (student) failures.add("CROSS_STUDENT_DATA");
        if (identity) failures.add("IDENTITY_SENT");
        boolean replaced = "FAULTY".equals(c.modelBehavior()) && c.lastText() != null && !message.equals(c.lastText().strip());
        return new Result(c.caseId(), c.category().name(), c.expectedOutcome().name(), failures.isEmpty(), List.copyOf(failures), message,
                sources, t.toolNames(), t.providerRequests(), c.knownLimit(), replaced, cross, student, identity);
    }
}
