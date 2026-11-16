package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.AcademicQueryFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicProgressQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolDefinition;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantKnowledgeSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolExecutorImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeSearchService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** Spec file 56, section 17.2: the progress tool through a registry and executor built by hand. */
class ProgressToolTests {
    static final String TOOL = "get_my_academic_progress";
    static final AcademicProgressQueryResult A = new AcademicProgressQueryResult(new BigDecimal("2.57"), new BigDecimal("18"), 3, 9, 3,
            2026, "AVAILABLE");
    private final AcademicQueryService academicQuery = mock(AcademicQueryService.class);
    private final AcademicProgressQueryService progressQuery = mock(AcademicProgressQueryService.class);
    private final AssistantProgressSettings on = AssistantProgressSettings.of(true);

    AcademicAiToolExecutorImpl executor(AssistantKnowledgeSettings knowledge, AssistantProgressSettings progress) {
        return new AcademicAiToolExecutorImpl(new AcademicAiToolRegistryImpl(knowledge, progress), academicQuery,
                new AcademicAiToolResultMapper(), mock(KnowledgeSearchService.class), knowledge, progressQuery, progress);
    }

    @Test
    void notActive() {
        var registry = new AcademicAiToolRegistryImpl(AssistantKnowledgeSettings.inactive(), AssistantProgressSettings.inactive());
        assertThat(registry.getAvailableTools()).hasSize(8);
        assertThat(registry.isSupported(TOOL)).isFalse();
        var result = executor(AssistantKnowledgeSettings.inactive(), AssistantProgressSettings.inactive())
                .execute(new LlmToolCall("c", TOOL, Map.of()));
        assertThat(result.errorCode()).isEqualTo("UNSUPPORTED_AI_TOOL");
        verifyNoInteractions(progressQuery);
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeOrderAndSchema() {
        var nine = new AcademicAiToolRegistryImpl(AssistantKnowledgeSettings.inactive(), on).getAvailableTools();
        assertThat(nine).hasSize(9);
        assertThat(nine.get(8).name()).isEqualTo(TOOL);
        var ten = new AcademicAiToolRegistryImpl(AssistantKnowledgeSettings.active(4, 8000, 2), on).getAvailableTools();
        assertThat(ten).extracting(AcademicAiToolDefinition::name).hasSize(10).endsWith(TOOL, "search_university_knowledge");
        var schema = nine.get(8).parameterSchema();
        assertThat((Map<String, Object>) schema.get("properties")).isEmpty();
        assertThat(schema).containsEntry("additionalProperties", false).doesNotContainKey("required");
        assertThat(nine.get(8).description()).startsWith("Get the authenticated student's academic progress totals")
                .endsWith("It does not list courses or grades and does not say whether a requirement is met.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void argumentsAndResultMap() {
        when(progressQuery.getMyAcademicProgress()).thenReturn(A);
        var executor = executor(AssistantKnowledgeSettings.inactive(), on);
        var ok = executor.execute(new LlmToolCall("c", TOOL, Map.of()));
        assertThat(ok.success()).isTrue();
        assertThat(new ArrayList<>(((Map<String, Object>) ok.data()).keySet())).containsExactly("cgpa", "creditsEarned", "currentCourses",
                "currentEnrolledCredits", "studyYear", "academicYear", "dataStatus");
        assertThat((Map<String, Object>) ok.data()).containsEntry("cgpa", new BigDecimal("2.57")).containsEntry("dataStatus", "AVAILABLE");
        verify(progressQuery, times(1)).getMyAcademicProgress();
        var bad = new ArrayList<Map<String, Object>>();
        bad.add(null);
        bad.add(Map.of("studentId", 1));
        bad.add(Map.of("x", 1));
        for (var args : bad) assertThat(executor.execute(new LlmToolCall("c", TOOL, args)).errorCode()).isEqualTo("INVALID_AI_TOOL_ARGUMENTS");
        verify(progressQuery, times(1)).getMyAcademicProgress();
        when(progressQuery.getMyAcademicProgress()).thenReturn(new AcademicProgressQueryResult(null, null, null, null, null, null, "PARTIAL"));
        var partial = (Map<String, Object>) executor.execute(new LlmToolCall("c", TOOL, Map.of())).data();
        assertThat(partial).hasSize(7).containsEntry("cgpa", null).containsEntry("creditsEarned", null).containsEntry("dataStatus", "PARTIAL");
        assertThat(partial.toString()).doesNotContainIgnoringCase("student").doesNotContain("course\"");
    }

    @Test
    void failuresAndAnExecutorWithoutTheService() {
        var executor = executor(AssistantKnowledgeSettings.inactive(), on);
        when(progressQuery.getMyAcademicProgress()).thenThrow(new DataAccessResourceFailureException("down"));
        assertThat(executor.execute(new LlmToolCall("c", TOOL, Map.of())).errorCode()).isEqualTo("AI_TOOL_EXECUTION_FAILED");
        reset(progressQuery);
        when(progressQuery.getMyAcademicProgress()).thenThrow(new AcademicQueryFailure(404, "STUDENT_NOT_FOUND", "x"));
        assertThat(executor.execute(new LlmToolCall("c", TOOL, Map.of())).errorCode()).isEqualTo("STUDENT_NOT_FOUND");
        var without = new AcademicAiToolExecutorImpl(new AcademicAiToolRegistryImpl(AssistantKnowledgeSettings.inactive(), on), academicQuery,
                new AcademicAiToolResultMapper());
        assertThat(without.execute(new LlmToolCall("c", TOOL, Map.of())).errorCode()).isEqualTo("UNSUPPORTED_AI_TOOL");
    }
}
