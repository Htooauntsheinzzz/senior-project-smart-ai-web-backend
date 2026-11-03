package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.GroundingEvaluationCase.Outcome;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.GroundingEvaluationCase.Scenario;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Structure of the Step 11 dataset: spec §68 minimum counts, unique ids, known facts, mutations and tools. */
class GroundingEvaluationDatasetTests {
    private static final GroundingEvaluationDataset DATASET = GroundingEvaluationDataset.get();
    private static final Set<String> MUTATIONS = Set.of("CS301_SEC01_ROOM_11_405", "CS301_SEC01_ROOM_NULL", "CS301_SEC01_LECTURER_LEE",
            "DROP_A_CS301", "CS301_DESCRIPTION_INJECTION");
    private static final Set<String> TOOLS = Set.of("get_my_academic_profile", "get_my_enrollments", "get_my_courses", "get_my_schedule",
            "get_my_course_section", "get_my_course_lecturer", "get_course_information", "get_section_availability");

    @Test
    void everyScenarioMeetsTheSpecMinimumAndTheTotalIsAtLeast85() {
        Map<Scenario, Long> counts = DATASET.cases().stream().collect(Collectors.groupingBy(GroundingEvaluationCase::scenario, Collectors.counting()));
        DATASET.minimumCases().forEach((scenario, minimum) -> assertThat(counts.getOrDefault(scenario, 0L)).as(scenario.name()).isGreaterThanOrEqualTo(minimum));
        assertThat(DATASET.minimumCases()).hasSize(Scenario.values().length);
        assertThat(DATASET.cases()).hasSizeGreaterThanOrEqualTo(85);
    }

    @Test
    void casesAreWellFormed() {
        assertThat(DATASET.cases().stream().map(GroundingEvaluationCase::caseId)).doesNotHaveDuplicates();
        for (var c : DATASET.cases()) {
            assertThat(c.turns()).as(c.caseId()).isNotEmpty();
            assertThat(c.turns()).as(c.caseId()).allSatisfy(t -> assertThat(t.script()).isNotEmpty());
            assertThat(c.modelBehavior()).as(c.caseId()).isIn("HONEST", "FAULTY");
            assertThat(c.testStudent()).as(c.caseId()).isIn("STD-TEST-001", "STD-TEST-002", "STD-TEST-003");
            assertThat(c.expectedFacts()).as(c.caseId()).allMatch(DATASET::knowsFact);
            if (c.expectedOutcome() == Outcome.ANSWER) assertThat(c.expectedFacts()).as(c.caseId()).isNotEmpty();
            assertThat(c.setup()).as(c.caseId()).allMatch(MUTATIONS::contains);
            c.turns().forEach(t -> assertThat(t.mutateBefore()).as(c.caseId()).allMatch(MUTATIONS::contains));
            assertThat(c.failTools()).as(c.caseId()).allMatch(TOOLS::contains);
            if (c.scenario() == Scenario.TOOL_FAILURE) assertThat(c.failTools()).as(c.caseId()).isNotEmpty();
            if (c.scenario() == Scenario.CONVERSATION_FRESHNESS) assertThat(c.turns()).as(c.caseId()).hasSizeGreaterThan(1);
        }
        // Both honest and faulty models are covered, so the backend's blocking is measured, not only the happy path.
        assertThat(DATASET.cases().stream().filter(c -> "FAULTY".equals(c.modelBehavior())).count()).isGreaterThanOrEqualTo(50);
    }
}
