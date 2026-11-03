package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolDefinition;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluationCase.Category;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluationCase.Split;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntent;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntentCatalog;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Step 9 dataset structure (spec §6, §18-21, §36, §44): size, coverage, labels, schemas and the holdout split. */
class IntentEvaluationDatasetTests {
    private static final IntentEvaluationDataset DATASET = IntentEvaluationDataset.get();
    private final Map<String, AcademicAiToolDefinition> tools = new AcademicAiToolRegistryImpl().getAvailableTools().stream()
            .collect(Collectors.toMap(AcademicAiToolDefinition::name, t -> t));

    @Test
    void datasetLoadsWithUniqueIdsAndTheRequiredSize() {
        List<String> ids = DATASET.cases().stream().map(IntentEvaluationCase::caseId).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(DATASET.version()).isEqualTo("1.0");
        assertThat(DATASET.cases(Category.PARAPHRASE)).hasSizeGreaterThanOrEqualTo(100);
        assertThat(DATASET.cases(Category.AMBIGUOUS)).hasSizeGreaterThanOrEqualTo(10);
        assertThat(DATASET.cases(Category.MISSING_DATA)).hasSizeGreaterThanOrEqualTo(10);
        assertThat(DATASET.cases(Category.SECURITY)).hasSizeGreaterThanOrEqualTo(10);
        assertThat(DATASET.cases()).hasSizeGreaterThanOrEqualTo(130);
        // Every JSONL line is one case.
        int lines = IntentEvaluationDataset.CASE_FILES.stream().mapToInt(f -> IntentEvaluationDataset.rawLines(f).size()).sum();
        assertThat(lines).isEqualTo(DATASET.cases().size());
    }

    @Test
    void everyIntentHasTenOrMoreVariedParaphrasesOnItsCatalogueTool() {
        var byIntent = DATASET.cases(Category.PARAPHRASE).stream().collect(Collectors.groupingBy(IntentEvaluationCase::intent));
        assertThat(byIntent.keySet()).containsExactlyInAnyOrderElementsOf(Arrays.stream(AcademicIntent.values()).map(Enum::name).toList());
        byIntent.forEach((intent, cases) -> {
            assertThat(cases).as(intent).hasSizeGreaterThanOrEqualTo(10);
            // Variation in wording and syntax, not one replaced word (spec §19).
            assertThat(cases.stream().map(IntentEvaluationCase::variationType).distinct().count()).as(intent).isGreaterThanOrEqualTo(6);
            assertThat(cases.stream().map(c -> c.input().toLowerCase(Locale.ROOT)).distinct().count()).isEqualTo(cases.size());
            var backing = AcademicIntentCatalog.of(AcademicIntent.valueOf(intent)).backingTools();
            cases.forEach(c -> assertThat(backing).as(c.caseId()).contains(c.expectedPrimaryTool()));
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void expectedToolsAndArgumentsMatchTheRegisteredSchemas() {
        for (IntentEvaluationCase c : DATASET.cases()) {
            for (String tool : c.expectedTools()) assertThat(tools).as(c.caseId()).containsKey(tool);
            c.acceptableToolSets().forEach(set -> set.forEach(tool -> assertThat(tools).as(c.caseId()).containsKey(tool)));
            if (c.expectedPrimaryTool() == null) {
                assertThat(c.expectedArguments()).as(c.caseId()).isEmpty();
                continue;
            }
            var schema = tools.get(c.expectedPrimaryTool()).parameterSchema();
            Set<String> properties = ((Map<String, Object>) schema.get("properties")).keySet();
            assertThat(properties).as(c.caseId()).containsAll(c.expectedArguments().keySet());
            assertThat(c.expectedArguments()).doesNotContainKey("studentId");
            for (Object required : (List<Object>) schema.getOrDefault("required", List.of()))
                if (c.category() == Category.PARAPHRASE) assertThat(c.expectedArguments()).as(c.caseId()).containsKey((String) required);
        }
    }

    @Test
    void groundTruthComesFromTheFixtureExpectations() {
        var expectations = DATASET.expectations();
        for (IntentEvaluationCase c : DATASET.cases()) {
            c.expectedFacts().forEach(key -> assertThat(expectations.facts()).as(c.caseId()).containsKey(key));
            if (c.expectedBehavior() == IntentEvaluationCase.Behavior.ANSWER) assertThat(c.expectedFacts()).as(c.caseId()).isNotEmpty();
            // The label is an evaluation annotation, never part of what the Student sends.
            assertThat(c.input()).doesNotContain("GET_MY_", "expectedIntent", "studentId");
        }
        assertThat(expectations.otherStudentFacts()).containsKeys("STD-TEST-001", "STD-TEST-002", "STD-TEST-003");
        assertThat(expectations.entityUniverse()).contains("John Smith", "11-402", "CS301");
        assertThat(expectations.fixtureVersion()).isEqualTo(com.smartAiUniversityAssistant.seniorproject.feature.assistant.AssistantAcademicFixture.FIXTURE_VERSION);
    }

    @Test
    void aboutOneFifthIsAFixedHoldoutInEveryCategory() {
        for (Category category : Category.values()) {
            var cases = DATASET.cases(category);
            long holdout = cases.stream().filter(c -> c.split() == Split.HOLDOUT).count();
            assertThat(holdout * 100.0 / cases.size()).as(category.name()).isBetween(15.0, 25.0);
        }
        var byIntent = DATASET.cases(Category.PARAPHRASE).stream().filter(c -> c.split() == Split.HOLDOUT)
                .collect(Collectors.groupingBy(IntentEvaluationCase::intent, Collectors.counting()));
        assertThat(byIntent).hasSize(10).allSatisfy((intent, count) -> assertThat(count).isEqualTo(2L));
    }

    @Test
    void variationsCoverLanguagesAndDeclaredEdgeBehaviour() {
        assertThat(DATASET.cases().stream().map(IntentEvaluationCase::language).distinct()).contains("en", "th");
        assertThat(DATASET.cases(Category.PARAPHRASE).stream().map(IntentEvaluationCase::variationType).distinct())
                .contains("direct", "polite", "informal", "short", "synonym", "indirect", "reformulation", "academic",
                        "conversational", "alternate_structure", "typo", "multilingual");
        assertThat(DATASET.cases(Category.AMBIGUOUS)).allMatch(c -> c.expectedBehavior() == IntentEvaluationCase.Behavior.CLARIFY);
        assertThat(DATASET.cases(Category.MISSING_DATA)).allMatch(c -> c.expectedBehavior() == IntentEvaluationCase.Behavior.NO_DATA);
        assertThat(DATASET.cases(Category.SECURITY).stream().filter(c -> !c.testStudent().equals("STD-TEST-001"))).isNotEmpty();
        // Each security case has a script for Mode A; unknown tools in scripts are what the backend must reject.
        assertThat(DATASET.cases(Category.SECURITY)).allMatch(c -> !c.effectiveScript().isEmpty());
    }
}
