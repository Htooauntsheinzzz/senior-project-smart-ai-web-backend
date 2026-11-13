package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProfileQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.exception.AcademicQueryFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolDefinition;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.AcademicAiToolResult;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantKnowledgeSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolExecutorImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeSearchHit;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeSearchService;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.StudentSearchOptions;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.StudentKnowledgeScope;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/** Spec file 53, section 20.2: the ninth tool through a registry and executor built by hand. */
class KnowledgeSearchToolTests {
    static final String TOOL = "search_university_knowledge";
    private final AcademicQueryService academicQuery = mock(AcademicQueryService.class);
    private final KnowledgeSearchService search = mock(KnowledgeSearchService.class);
    private final AssistantKnowledgeSettings active = AssistantKnowledgeSettings.active(4, 8000, 2);
    private final AcademicAiToolRegistryImpl registry = new AcademicAiToolRegistryImpl(active);
    private final AcademicAiToolExecutorImpl executor = new AcademicAiToolExecutorImpl(registry, academicQuery,
            new AcademicAiToolResultMapper(), search, active);

    static AcademicProfileQueryResult profile(Long programId, Long departmentId) {
        return new AcademicProfileQueryResult(7L, "S-1", "Test Student", "s@rsu.ac.th", 1L, "IT", "IT", null,
                departmentId, "CS", "Computer Science", programId, "CS", "Computer Science", 3L, 2026, "Semester 1", null, 3, 2024);
    }

    static KnowledgeSearchHit hit(int rank, String title, String category, String section, String unit, int from, int to, String text) {
        return new KnowledgeSearchHit(rank, 0.9, 100 + rank, title, category, "2026-2027", section, unit, from, to, text);
    }

    private AcademicAiToolResult run(Map<String, Object> args) {
        return executor.execute(new LlmToolCall("c1", TOOL, args));
    }

    private static Map<String, Object> args(Object... pairs) {
        var map = new HashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }

    @Test
    void notActiveMeansEightToolsAndUnsupported() {
        var inactive = new AcademicAiToolRegistryImpl();
        assertThat(inactive.getAvailableTools()).hasSize(8).extracting(AcademicAiToolDefinition::name).doesNotContain(TOOL);
        assertThat(inactive.isSupported(TOOL)).isFalse();
        var executorOff = new AcademicAiToolExecutorImpl(inactive, academicQuery, new AcademicAiToolResultMapper());
        var result = executorOff.execute(new LlmToolCall("c", TOOL, Map.of("query", "fees")));
        assertThat(result.errorCode()).isEqualTo("UNSUPPORTED_AI_TOOL");
        verifyNoInteractions(academicQuery, search);
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeMeansNineToolsWithAClosedQueryOnlySchema() {
        assertThat(registry.getAvailableTools()).hasSize(9);
        AcademicAiToolDefinition tool = registry.getAvailableTools().get(8);
        assertThat(tool.name()).isEqualTo(TOOL);
        assertThat(registry.isSupported(TOOL)).isTrue();
        assertThat(tool.description()).startsWith("Search the university's official documents").contains("An empty list means no relevant passage was found.");
        var schema = tool.parameterSchema();
        assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false).containsEntry("required", List.of("query"));
        var properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsOnlyKeys("query");
        assertThat((Map<String, Object>) properties.get("query")).containsEntry("type", "string").containsEntry("maxLength", 300);
        for (String forbidden : List.of("student", "program", "department", "document"))
            assertThat(properties.keySet()).noneMatch(k -> k.toLowerCase(Locale.ROOT).contains(forbidden));
    }

    @Test
    void validCallSearchesWithTheProfileScopeAndTopK() {
        when(academicQuery.getMyAcademicProfile()).thenReturn(profile(11L, 22L));
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of());
        var result = run(args("query", "  minimum credits to graduate  "));
        verify(search).studentSearch("minimum credits to graduate", new StudentKnowledgeScope(11L, 22L), new StudentSearchOptions(4, 0, 0));
        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(List.of());
        assertThat(result.resultCount()).isZero();
    }

    @Test
    void profileWithoutProgramAndDepartmentGivesTwoNulls() {
        when(academicQuery.getMyAcademicProfile()).thenReturn(profile(null, null));
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of());
        run(args("query", "fees"));
        verify(search).studentSearch("fees", new StudentKnowledgeScope(null, null), new StudentSearchOptions(4, 0, 0));
    }

    @Test
    void invalidArgumentsNeverReachTheSearch() {
        var cases = new ArrayList<Map<String, Object>>();
        cases.add(null);
        cases.add(Map.of());
        cases.add(args("query", 42));
        cases.add(args("query", "   "));
        cases.add(args("query", "x".repeat(301)));
        for (String extra : List.of("studentId", "programId", "departmentId", "documentId", "topK"))
            cases.add(args("query", "fees", extra, 1));
        for (var a : cases) {
            var result = executor.execute(new LlmToolCall("c", TOOL, a));
            assertThat(result.success()).as(String.valueOf(a)).isFalse();
            assertThat(result.errorCode()).isEqualTo("INVALID_AI_TOOL_ARGUMENTS");
        }
        verifyNoInteractions(search, academicQuery);
        when(academicQuery.getMyAcademicProfile()).thenReturn(profile(1L, 2L));
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of());
        assertThat(run(args("query", "x".repeat(300))).success()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void passageMaps() {
        when(academicQuery.getMyAcademicProfile()).thenReturn(profile(1L, 2L));
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(
                hit(1, "Regulations", "Regulations", "Chapter 5 Graduation", "PAGE", 12, 13, "Article 11."),
                hit(2, "Regulations", "Regulations", null, "PAGE", 12, 12, "Article 12."),
                hit(3, "Handbook", null, "Section 1", "SECTION", 1, 1, "docx"),
                hit(4, "Fees", null, null, "SHEET", 1, 1, "xlsx")));
        var data = (List<Map<String, Object>>) run(args("query", "graduation")).data();
        assertThat(data).hasSize(4);
        assertThat(data.get(0)).containsExactly(Map.entry("documentTitle", "Regulations"), Map.entry("category", "Regulations"),
                Map.entry("academicYear", "2026-2027"), Map.entry("section", "Chapter 5 Graduation"), Map.entry("pages", "12-13"),
                Map.entry("text", "Article 11."));
        assertThat(data.get(1)).containsEntry("pages", "12").doesNotContainKey("section");
        assertThat(data.get(2)).doesNotContainKeys("pages", "category").containsEntry("section", "Section 1");
        assertThat(data.get(3)).containsOnlyKeys("documentTitle", "academicYear", "text");
        data.forEach(p -> assertThat(p.keySet()).doesNotContain("id", "documentId", "score", "rank", "fileName", "scope"));
        // TXT
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(hit(1, "T", null, null, "TEXT", 1, 1, "t")));
        assertThat(((List<Map<String, Object>>) run(args("query", "x")).data()).get(0)).doesNotContainKey("pages");
    }

    @Test
    void contextBudget() {
        var settings = AssistantKnowledgeSettings.active(4, 2000, 2);
        var small = new AcademicAiToolExecutorImpl(new AcademicAiToolRegistryImpl(settings), academicQuery,
                new AcademicAiToolResultMapper(), search, settings);
        when(academicQuery.getMyAcademicProfile()).thenReturn(profile(1L, 2L));
        String nine = "n".repeat(900);
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(hit(1, "A", null, null, "TEXT", 1, 1, nine),
                hit(2, "A", null, null, "TEXT", 1, 1, nine), hit(3, "A", null, null, "TEXT", 1, 1, nine), hit(4, "A", null, null, "TEXT", 1, 1, nine)));
        var result = small.execute(new LlmToolCall("c", TOOL, Map.of("query", "x")));
        assertThat((List<?>) result.data()).hasSize(2);
        assertThat(result.resultCount()).isEqualTo(2);
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(hit(1, "A", null, null, "TEXT", 1, 1, "l".repeat(3000)),
                hit(2, "A", null, null, "TEXT", 1, 1, "short")));
        assertThat((List<?>) small.execute(new LlmToolCall("c", TOOL, Map.of("query", "x"))).data()).hasSize(1);
    }

    @Test
    void failures() {
        when(academicQuery.getMyAcademicProfile()).thenReturn(profile(1L, 2L));
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenThrow(KnowledgeFailure.invalid());
        assertThat(run(args("query", "x")).errorCode()).isEqualTo("INVALID_AI_TOOL_ARGUMENTS");
        for (var failure : List.of(KnowledgeFailure.searchNotConfigured(), KnowledgeFailure.searchRateLimited(), KnowledgeFailure.searchUnavailable())) {
            reset(search);
            when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenThrow(failure);
            var result = run(args("query", "secret query text"));
            assertThat(result.errorCode()).isEqualTo(failure.code());
            assertThat(result.errorMessage()).isEqualTo("The university documents could not be searched right now.");
            assertThat(result.payload().toString()).doesNotContain("secret query text");
        }
        reset(search);
        when(academicQuery.getMyAcademicProfile()).thenThrow(new DataAccessResourceFailureException("db down"));
        assertThat(run(args("query", "x")).errorCode()).isEqualTo("AI_TOOL_EXECUTION_FAILED");
        reset(academicQuery);
        when(academicQuery.getMyAcademicProfile()).thenThrow(new AcademicQueryFailure(404, "STUDENT_PROFILE_NOT_FOUND", "not found"));
        assertThat(run(args("query", "x")).errorCode()).isEqualTo("STUDENT_PROFILE_NOT_FOUND");
        verifyNoInteractions(search);
    }
}
