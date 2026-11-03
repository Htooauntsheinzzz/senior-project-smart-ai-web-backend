package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AcademicAiToolNames.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolDefinition;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntent;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntentCatalog;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.intent.AcademicIntentCatalog.Definition;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.PersonalAcademicQuestionDetector;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Step 8: the ten-intent catalogue, its mapping onto the eight existing tools, and the regression dataset's shape. */
class AcademicIntentCatalogTests {
    private final List<AcademicAiToolDefinition> tools = new AcademicAiToolRegistryImpl().getAvailableTools();

    @Test
    void catalogueHasExactlyTheTenIntentsOnceEach() {
        List<AcademicIntent> intents = AcademicIntentCatalog.all().stream().map(Definition::intent).toList();
        assertThat(intents).hasSize(10).doesNotHaveDuplicates().containsExactlyInAnyOrder(AcademicIntent.values());
        for (AcademicIntent intent : AcademicIntent.values()) {
            Definition definition = AcademicIntentCatalog.of(intent);
            assertThat(definition.backingTools()).isNotEmpty();
            assertThat(definition.answerFocus()).isNotBlank();
            assertThat(definition.purpose()).isNotBlank();
        }
    }

    @Test
    void tenIntentsReuseTheEightExistingTools() {
        Set<String> registered = tools.stream().map(AcademicAiToolDefinition::name).collect(Collectors.toSet());
        assertThat(registered).hasSize(8);
        Set<String> used = AcademicIntentCatalog.all().stream().flatMap(d -> d.backingTools().stream()).collect(Collectors.toSet());
        assertThat(registered).containsAll(used);
        assertThat(used).isEqualTo(registered);
        // The two answer-level specialisations have no tool of their own.
        assertThat(AcademicIntentCatalog.of(AcademicIntent.GET_MY_PROGRAM).backingTools()).containsExactly(GET_MY_ACADEMIC_PROFILE);
        assertThat(AcademicIntentCatalog.of(AcademicIntent.GET_COURSE_CREDITS).backingTools()).containsExactly(GET_COURSE_INFORMATION);
        assertThat(registered).doesNotContain("get_course_credits", "get_my_program");
    }

    @Test
    void sharedToolsAreToldApartByAnswerFocus() {
        assertThat(AcademicIntentCatalog.of(AcademicIntent.GET_MY_PROGRAM).answerFocus()).contains("program name only")
                .contains("never the faculty or department");
        assertThat(AcademicIntentCatalog.of(AcademicIntent.GET_COURSE_CREDITS).answerFocus()).contains("creditHours value only");
        assertThat(AcademicIntentCatalog.of(AcademicIntent.GET_MY_COURSES).answerFocus()).contains("not enrollment records");
        String prompt = AcademicIntentCatalog.promptSection();
        AcademicIntentCatalog.all().forEach(d -> assertThat(prompt).contains(d.answerFocus()));
        // The tool descriptions the model sees name the specialised questions too.
        assertThat(description(GET_MY_ACADEMIC_PROFILE)).contains("program / major / degree").contains("programName");
        assertThat(description(GET_COURSE_INFORMATION)).contains("How many credits").contains("creditHours");
        assertThat(description(GET_SECTION_AVAILABILITY)).contains("never derive it from a section label");
    }

    @Test
    void entityRequirementsMatchTheToolSchemas() {
        for (Definition definition : AcademicIntentCatalog.all()) {
            @SuppressWarnings("unchecked")
            Set<String> properties = ((Map<String, Object>) tool(definition.backingTools().getFirst()).parameterSchema()
                    .get("properties")).keySet();
            switch (definition.requiredEntity()) {
                case NONE -> assertThat(properties).isEmpty();
                case COURSE_IDENTIFIER -> assertThat(properties).containsExactly("courseIdentifier");
                case COURSE_SECTION_ID -> assertThat(properties).containsExactly("courseSectionId");
            }
        }
        for (AcademicAiToolDefinition tool : tools) {
            assertThat(tool.parameterSchema()).containsEntry("additionalProperties", false);
            assertThat(tool.parameterSchema().toString()).doesNotContain("studentId");
        }
    }

    @Test
    void regressionDatasetCoversEveryIntentWithFiveOrMoreParaphrasesAndTwentyEdgeCases() {
        var positive = IntentRegressionDataset.cases("positive");
        var negative = IntentRegressionDataset.cases("negative");
        assertThat(positive).hasSizeGreaterThanOrEqualTo(50);
        assertThat(negative).hasSizeGreaterThanOrEqualTo(20);
        var ids = new ArrayList<Object>();
        positive.forEach(c -> ids.add(c.get("caseId")));
        negative.forEach(c -> ids.add(c.get("caseId")));
        assertThat(ids).doesNotHaveDuplicates();
        Map<Object, Long> perIntent = positive.stream().collect(Collectors.groupingBy(c -> c.get("expectedIntent"), Collectors.counting()));
        for (AcademicIntent intent : AcademicIntent.values()) assertThat(perIntent.get(intent.name())).as(intent.name()).isGreaterThanOrEqualTo(5L);
        for (var testCase : positive) {
            Definition definition = AcademicIntentCatalog.of(AcademicIntent.valueOf((String) testCase.get("expectedIntent")));
            @SuppressWarnings("unchecked") var expected = (List<String>) testCase.get("expectedTools");
            // The intent's own tool is always the last call; availability may first resolve the student's section.
            assertThat(expected.getLast()).as((String) testCase.get("caseId")).isEqualTo(definition.backingTools().getFirst());
        }
    }

    @Test
    void backendEvidenceCheckCoversTheDatasetAsDocumented() {
        // evidenceRequired=false marks the known gap: a catalogue question that names a course only by its name.
        for (var testCase : IntentRegressionDataset.cases("positive")) {
            String utterance = ((String) testCase.get("utterance")).replace("${CS301_SEC01}", "15");
            assertThat(PersonalAcademicQuestionDetector.requiresAcademicEvidence(utterance)).as(utterance)
                    .isEqualTo(testCase.get("evidenceRequired"));
        }
        long enforced = IntentRegressionDataset.cases("positive").stream().filter(c -> Boolean.TRUE.equals(c.get("evidenceRequired"))).count();
        assertThat(enforced).isGreaterThanOrEqualTo(50);
    }

    private AcademicAiToolDefinition tool(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }
    private String description(String name) { return tool(name).description(); }
}
