package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluationCase.Category;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntentCatalog;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;

/**
 * Step 9 Mode A (spec §23): every dataset case through the real chat endpoint, JWT, tool executor,
 * AcademicQueryService and PostgreSQL, with Gemini replaced by the case's scripted tool calls. It verifies the backend
 * pipeline and the evaluator (tool registry, argument validation, Student isolation, facts read from the database,
 * error handling, report generation), not Gemini's language understanding; that is Mode B
 * ({@link GeminiLiveIntentEvaluationTests}). Report: target/step09-evaluation-deterministic/.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeterministicIntentEvaluationTests extends IntentEvaluationSupport {
    private static final IntentEvaluationDataset DATASET = IntentEvaluationDataset.get();
    private final IntentEvaluator evaluator = new IntentEvaluator(DATASET.expectations());
    private final List<IntentEvaluationResult> results = Collections.synchronizedList(new ArrayList<>());

    static Stream<Named<IntentEvaluationCase>> cases() {
        return DATASET.cases().stream().map(c -> Named.of(c.caseId() + " " + c.input(), c));
    }

    @BeforeAll
    void createData() { createFixture(); }

    @AfterAll
    void writeReport() throws Exception {
        fixture.clean();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("mode", "DETERMINISTIC_SCRIPTED_MODEL");
        metadata.put("datasetVersion", DATASET.version());
        metadata.put("fixtureVersion", DATASET.expectations().fixtureVersion());
        metadata.put("model", "scripted (no Gemini call)");
        metadata.put("runAt", Instant.now().toString());
        metadata.put("trials", 1);
        metadata.put("totalCases", DATASET.cases().size());
        IntentEvaluationReportWriter.write(Path.of("target", "step09-evaluation-deterministic"), new IntentEvaluationMetrics(results), metadata);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void datasetCasePassesThroughTheBackendPipeline(IntentEvaluationCase raw) throws Exception {
        IntentEvaluationCase c = raw.resolve(fixture.placeholders());
        recorder.delegateTo(new ScriptedLlmClient(scriptWithAnswer(c)));
        EvaluationTrace trace = run(c);
        IntentEvaluationResult result = evaluator.evaluate(c, trace, 1);
        results.add(result);

        assertThat(result.failureCategories()).as(c.caseId() + " " + trace.message()).isEmpty();
        assertThat(result.passed()).isTrue();
        assertThat(trace.dataModified()).isFalse();
        List<String> failedCodes = trace.toolCalls().stream().filter(call -> !call.success()).map(EvaluationTrace.ObservedToolCall::code).toList();
        assertThat(failedCodes).isEqualTo(c.expectedToolCodes());
        List<LlmRequest> sent = recorder.requests();
        assertThat(sent.getFirst().systemPrompt()).contains(AcademicIntentCatalog.promptSection());
        // The Student's request carries only the message: no intent label, no student id.
        assertThat(sent).allMatch(r -> r.userMessage().equals(c.input()));
        if (c.category() == Category.PARAPHRASE) {
            assertThat(result.toolCorrect()).isTrue();
            assertThat(result.argumentsCorrect()).isNotEqualTo(Boolean.FALSE);
            assertThat(sent).noneMatch(LlmRequest::requireTool);
        }
    }

    @Test
    void studentIdentityComesFromTheTokenSoTheSameQuestionGivesDifferentRecords() throws Exception {
        var question = DATASET.cases().stream().filter(c -> c.caseId().equals("NLU-LECTURER-001")).findFirst().orElseThrow();
        var asB = new IntentEvaluationCase(question.caseId(), question.category(), question.intent(), question.input(),
                question.variationType(), question.language(), question.split(), "STD-TEST-002", question.expectedBehavior(),
                question.expectedPrimaryTool(), question.expectedTools(), List.of(), question.expectedArguments(),
                List.of("CS301_LECTURER_B"), List.of("John Smith"), List.of(), List.of());
        recorder.delegateTo(new ScriptedLlmClient(scriptWithAnswer(question)));
        String forA = run(question).toolResultsJson();
        recorder.delegateTo(new ScriptedLlmClient(scriptWithAnswer(asB)));
        String forB = run(asB).toolResultsJson();
        assertThat(forA).contains("Dr. John Smith", "\"sectionNumber\":\"SEC-01\"").doesNotContain("Dr. Jane Doe");
        assertThat(forB).contains("Dr. Jane Doe", "\"sectionNumber\":\"SEC-02\"").doesNotContain("Dr. John Smith");
    }

    @Test
    void repeatedRunsReadTheSameRecords() throws Exception {
        var c = DATASET.cases().stream().filter(x -> x.caseId().equals("NLU-SCHEDULE-001")).findFirst().orElseThrow();
        recorder.delegateTo(new ScriptedLlmClient(scriptWithAnswer(c)));
        String first = run(c).toolResultsJson();
        recorder.delegateTo(new ScriptedLlmClient(scriptWithAnswer(c)));
        assertThat(run(c).toolResultsJson()).isEqualTo(first);
    }

    @Test
    void evaluatorFlagsFaultyModelBehaviourEndToEnd() throws Exception {
        var lecturer = find("NLU-LECTURER-007");
        assertThat(grade(lecturer, List.of(tools("get_course_information", Map.of("courseIdentifier", "CS301")),
                Map.of("text", "CS301 is Data Structures.")))).satisfies(r -> {
            assertThat(r.toolCorrect()).isFalse();
            assertThat(r.failureCategories()).contains("WRONG_TOOL_SELECTED");
        });
        assertThat(grade(lecturer, List.of(tools("get_my_course_lecturer", Map.of("courseIdentifier", "CS302")),
                Map.of("text", "I could not find CS302.")))).satisfies(r -> {
            assertThat(r.argumentsCorrect()).isFalse();
            assertThat(r.failureCategories()).contains("COURSE_IDENTIFIER_EXTRACTION_ERROR", "ACADEMIC_QUERY_ERROR");
        });
        // Step 11: the backend rejects an invented lecturer and room before the student sees them, so the evaluator
        // grades the grounded reply (IntentEvaluatorTests still covers the evaluator's own detection).
        assertThat(grade(lecturer, List.of(tools("get_my_course_lecturer", Map.of("courseIdentifier", "CS301")),
                Map.of("text", "Your CS301 lecturer is Dr. Jane Lee in room 9-101.")))).satisfies(r -> {
            assertThat(r.hallucinationDetected()).isFalse();
            assertThat(r.finalResponse()).contains("Dr. John Smith").doesNotContain("Jane Lee").doesNotContain("9-101");
            assertThat(r.taskCompleted()).isTrue();
        });
        assertThat(grade(lecturer, List.of(Map.of("providerError", 429)))).satisfies(r -> {
            assertThat(r.providerFailure()).isTrue();
            assertThat(r.failureCategories()).containsExactly("GEMINI_PROVIDER_FAILURE");
            assertThat(r.toolCorrect()).isNull();
        });
        // A strict tool difference that still answers correctly: task completed, strict accuracy not.
        assertThat(grade(find("NLU-ENROLL-003"), List.of(tools("get_my_courses", Map.of()),
                Map.of("text", "CS301, CSC452 and CSC460.")))).satisfies(r -> {
            assertThat(r.toolCorrect()).isFalse();
            assertThat(r.semanticToolCorrect()).isTrue();
            assertThat(r.taskCompleted()).isTrue();
        });
        // Picking a course the question never named is an invented selection.
        assertThat(grade(find("AMB-002"), List.of(tools("get_my_course_lecturer", Map.of("courseIdentifier", "CS301")),
                Map.of("text", "Dr. John Smith teaches CS301.")))).satisfies(r -> {
            assertThat(r.guessedIdentifier()).isTrue();
            assertThat(r.failureCategories()).contains("AMBIGUITY_HANDLING_ERROR");
        });
    }

    @Test
    void toolCallLimitIsEnforcedAndGraded() throws Exception {
        var c = find("NLU-COURSES-001");
        var loop = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 12; i++) loop.add(tools("get_my_courses", Map.of()));
        IntentEvaluationResult r = grade(c, loop);
        assertThat(r.httpStatus()).isEqualTo(502);
        assertThat(r.errorCode()).isEqualTo("AI_TOOL_CALL_LIMIT_EXCEEDED");
        assertThat(r.failureCategories()).contains("TOOL_CALL_LIMIT_EXCEEDED");
        assertThat(r.providerFailure()).isFalse();
    }

    private IntentEvaluationResult grade(IntentEvaluationCase raw, List<Map<String, Object>> script) throws Exception {
        IntentEvaluationCase c = raw.resolve(fixture.placeholders());
        recorder.delegateTo(new ScriptedLlmClient(script));
        return evaluator.evaluate(c, run(c), 1);
    }

    private static IntentEvaluationCase find(String caseId) {
        return DATASET.cases().stream().filter(c -> c.caseId().equals(caseId)).findFirst().orElseThrow();
    }

    private static Map<String, Object> tools(String name, Map<String, Object> arguments) {
        return Map.of("tools", List.of(Map.of("name", name, "arguments", arguments)));
    }

    /** The case's tool calls followed by an answer built from the fixture's canonical facts (or the expected behaviour). */
    private static List<Map<String, Object>> scriptWithAnswer(IntentEvaluationCase c) {
        var steps = new ArrayList<>(c.effectiveScript());
        if (steps.isEmpty() || !steps.getLast().containsKey("text")) steps.add(Map.of("text", answer(c)));
        return steps;
    }

    private static String answer(IntentEvaluationCase c) {
        if (!c.expectedFacts().isEmpty())
            return "Scripted answer: " + String.join(", ", c.expectedFacts().stream().map(k -> DATASET.expectations().fact(k).getFirst()).toList()) + ".";
        return switch (c.expectedBehavior()) {
            case NO_DATA -> "That information is not available.";
            case CLARIFY -> "Which course are you asking about?";
            case REFUSE -> "I can only help with your own academic records.";
            case ANSWER -> throw new IllegalStateException(c.caseId() + " has no expected facts");
        };
    }

}
