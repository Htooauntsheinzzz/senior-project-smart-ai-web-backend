package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.LlmToolCall;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper.AcademicAiToolResultMapper;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantKnowledgeSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolExecutorImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec file 54, section 14.4: appliesTo, the search options and the context budget in two passes. */
class KnowledgeCurriculumToolTests {
    private final AcademicQueryService academicQuery = mock(AcademicQueryService.class);
    private final KnowledgeSearchService search = mock(KnowledgeSearchService.class);

    static KnowledgeSearchHit hit(int rank, String text, KnowledgeAudience audience, boolean neighbor) {
        return new KnowledgeSearchHit(rank, 0.9, 1, "Doc", "Curriculum", "2026-2027", "Section 4", "TEXT", 1, 1, text, audience, neighbor);
    }

    AcademicAiToolExecutorImpl executor(int maxContextChars) {
        var settings = AssistantKnowledgeSettings.active(4, maxContextChars, 2, 2, 2);
        return new AcademicAiToolExecutorImpl(new AcademicAiToolRegistryImpl(settings), academicQuery, new AcademicAiToolResultMapper(),
                search, settings);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> run(AcademicAiToolExecutorImpl executor) {
        return (List<Map<String, Object>>) executor.execute(new LlmToolCall("c", "search_university_knowledge", Map.of("query", "electives"))).data();
    }

    @Test
    void appliesToAndOptions() {
        when(academicQuery.getMyAcademicProfile()).thenReturn(KnowledgeSearchToolTests.profile(1L, 2L));
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(
                hit(1, "p", KnowledgeAudience.PROGRAM, false), hit(2, "d", KnowledgeAudience.DEPARTMENT, false),
                hit(3, "u", KnowledgeAudience.UNIVERSITY, false), hit(3, "n", KnowledgeAudience.UNIVERSITY, true)));
        var passages = run(executor(8000));
        verify(search).studentSearch("electives", new StudentKnowledgeScope(1L, 2L), new StudentSearchOptions(4, 2, 2));
        assertThat(passages).extracting(p -> p.get("appliesTo")).containsExactly("your program", "your department", "all students", "all students");
        assertThat(new ArrayList<>(passages.get(0).keySet())).containsExactly("documentTitle", "category", "academicYear", "appliesTo", "section", "text");
        passages.forEach(p -> assertThat(p).doesNotContainKeys("neighbor", "documentId", "score", "rank"));
    }

    @Test
    void contextBudgetInTwoPasses() {
        when(academicQuery.getMyAcademicProfile()).thenReturn(KnowledgeSearchToolTests.profile(1L, 2L));
        String h = "h".repeat(800), n = "n".repeat(300);
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(
                hit(1, n + "1b", KnowledgeAudience.PROGRAM, true), hit(1, h + "1", KnowledgeAudience.PROGRAM, false),
                hit(2, h + "2", KnowledgeAudience.PROGRAM, false), hit(2, n + "2a", KnowledgeAudience.PROGRAM, true)));
        var passages = run(executor(2000));
        assertThat(passages).extracting(p -> ((String) p.get("text")).replaceAll("[hn]+", "")).containsExactly("1b", "1", "2");

        // A neighbour whose hit did not fit is never added; a first hit of 3,000 characters is returned alone.
        when(search.studentSearch(any(), any(), any(StudentSearchOptions.class))).thenReturn(List.of(
                hit(1, "x".repeat(3000), KnowledgeAudience.PROGRAM, false), hit(2, "y".repeat(100), KnowledgeAudience.PROGRAM, false),
                hit(2, "z".repeat(10), KnowledgeAudience.PROGRAM, true)));
        var first = run(executor(2000));
        assertThat(first).hasSize(1);
        assertThat((String) first.get(0).get("text")).hasSize(3000);
    }
}
