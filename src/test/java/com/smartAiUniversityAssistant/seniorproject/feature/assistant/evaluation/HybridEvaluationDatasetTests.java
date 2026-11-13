package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.HybridEvaluationCase.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.HybridAcademicQuestionDetector;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Spec file 57, section 16.6: the structure of the Step 16 dataset. No Spring context, no provider. */
class HybridEvaluationDatasetTests {
    private static final HybridEvaluationDataset DATASET = HybridEvaluationDataset.get();
    private static final Set<String> TOOLS;
    static {
        var all = new HashSet<>(CurriculumEvaluationDatasetTests.TOOLS);
        all.add("get_my_academic_progress");
        TOOLS = Set.copyOf(all);
    }

    @Test
    void countsPerCategoryAndTotal() {
        Map<Category, Long> counts = DATASET.cases().stream().collect(Collectors.groupingBy(HybridEvaluationCase::category, Collectors.counting()));
        assertThat(DATASET.minimumCases()).hasSize(Category.values().length);
        DATASET.minimumCases().forEach((c, min) -> assertThat(counts.getOrDefault(c, 0L)).as(c.name()).isGreaterThanOrEqualTo(min));
        assertThat(DATASET.cases()).hasSizeGreaterThanOrEqualTo(Math.max(42, DATASET.minimumTotal()));
        assertThat(DATASET.version()).isEqualTo("1.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void casesAreWellFormed() {
        assertThat(DATASET.cases().stream().map(HybridEvaluationCase::caseId)).doesNotHaveDuplicates();
        for (int i = 1; i <= 29; i++) {
            String id = String.format("HYB-%03d", i);
            assertThat(DATASET.cases()).as(id).anyMatch(c -> c.caseId().equals(id));
        }
        for (var c : DATASET.cases()) {
            assertThat(c.script()).as(c.caseId()).isNotEmpty();
            for (var step : c.script())
                if (step.get("tools") instanceof List<?> calls)
                    calls.forEach(call -> assertThat(((Map<String, Object>) call).get("name")).as(c.caseId())
                            .isIn(TOOLS));
            assertThat(c.testStudent()).as(c.caseId()).isIn("STD-TEST-001", "STD-TEST-002", "STD-TEST-003");
            if (c.knownLimit()) assertThat(c.note()).as(c.caseId()).isNotBlank();
            boolean hybrid = HybridAcademicQuestionDetector.asksAboutOwnStanding(c.input());
            assertThat(hybrid).as(c.caseId() + " " + c.input()).isEqualTo(c.category() != Category.NOT_HYBRID);
        }
    }
}
