package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.CurriculumEvaluationCase.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Spec file 55, section 11.6: the structure of the Step 15 dataset. No Spring context, no provider. */
class CurriculumEvaluationDatasetTests {
    private static final CurriculumEvaluationDataset DATASET = CurriculumEvaluationDataset.get();
    static final Set<String> TOOLS = Set.of("get_my_academic_profile", "get_my_enrollments", "get_my_courses", "get_my_schedule",
            "get_my_course_section", "get_my_course_lecturer", "get_course_information", "get_section_availability",
            "search_university_knowledge");

    @Test
    void countsPerCategoryAndTotal() {
        Map<Category, Long> counts = DATASET.cases().stream().collect(Collectors.groupingBy(CurriculumEvaluationCase::category, Collectors.counting()));
        assertThat(DATASET.minimumCases()).hasSize(Category.values().length);
        DATASET.minimumCases().forEach((category, minimum) -> assertThat(counts.getOrDefault(category, 0L)).as(category.name())
                .isGreaterThanOrEqualTo(minimum));
        assertThat(DATASET.cases()).hasSizeGreaterThanOrEqualTo(Math.max(40, DATASET.minimumTotal()));
        assertThat(DATASET.version()).isEqualTo("1.0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void casesAreWellFormed() throws Exception {
        assertThat(DATASET.cases().stream().map(CurriculumEvaluationCase::caseId)).doesNotHaveDuplicates();
        for (int i = 1; i <= 16; i++) {
            String id = String.format("CUR-%03d", i);
            assertThat(DATASET.cases()).as(id).anyMatch(c -> c.caseId().equals(id));
        }
        String documents = Files.readString(Path.of("docker/dev-fixtures/rag-sample-regulations.txt"))
                + Files.readString(Path.of("docker/dev-fixtures/rag-sample-curriculum-cs.txt"));
        for (var c : DATASET.cases()) {
            assertThat(c.script()).as(c.caseId()).isNotEmpty();
            for (var step : c.script()) {
                assertThat(step.containsKey("text") || step.containsKey("tools")).as(c.caseId()).isTrue();
                if (step.get("tools") instanceof List<?> calls)
                    calls.forEach(call -> assertThat(((Map<String, Object>) call).get("name")).as(c.caseId()).isIn(TOOLS));
            }
            assertThat(c.testStudent()).as(c.caseId()).isIn("STD-TEST-001", "STD-TEST-002", "STD-TEST-003");
            assertThat(c.modelBehavior()).as(c.caseId()).isIn("HONEST", "FAULTY");
            if (c.knownLimit()) assertThat(c.note()).as(c.caseId()).isNotBlank();
            else assertThat(c.note()).as(c.caseId()).isNull();
            if (c.expectedOutcome() == Outcome.ANSWER) assertThat(c.lastText()).as(c.caseId()).isNotBlank();
            for (String value : c.mustContain())
                assertThat(documents.contains(value) || DATASET.fixtureValues().contains(value)).as(c.caseId() + " " + value).isTrue();
        }
    }
}
