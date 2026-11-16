package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidence;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantKnowledgeSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.AcademicAiToolRegistryImpl;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Spec file 55, sections 14.1 and 14.2: rules 23 to 27, the tool description and the passage block. */
class CurriculumPromptAndPassageTests {

    @Test
    void curriculumRulesFollowRule22OnlyWhenActive() {
        var t = new StudentAssistantKnowledgeServiceTests();
        t.setUp();
        when(t.llm.chat(any())).thenReturn(new LlmResponse("GPA means grade point average.", "m", null));
        t.chat("What does GPA mean?");
        t.service = t.service(AssistantKnowledgeSettings.inactive());
        t.chat("What does GPA mean?");
        var captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(t.llm, times(2)).chat(captor.capture());
        String active = captor.getAllValues().get(0).systemPrompt();
        String inactive = captor.getAllValues().get(1).systemPrompt();
        int rule22 = active.indexOf("22. The database tools remain");
        int rules = active.indexOf("CURRICULUM RULES");
        assertThat(rule22).isPositive();
        assertThat(rules).isGreaterThan(rule22);
        for (int n = 23; n <= 27; n++) assertThat(active.indexOf(n + ". ", rules)).as("rule " + n).isGreaterThan(rules);
        assertThat(active).contains("the value for the student's program is the one that applies to this student")
                .contains("never claim that a course is offered now").contains("found no curriculum passage");
        assertThat(inactive).doesNotContain("CURRICULUM RULES").doesNotContain("UNIVERSITY DOCUMENT RULES");
    }

    @Test
    @SuppressWarnings("unchecked")
    void toolDescriptionOfSection6() {
        var tool = new AcademicAiToolRegistryImpl(AssistantKnowledgeSettings.active(4, 8000, 2)).getAvailableTools().get(8);
        assertThat(tool.description()).isEqualTo("Search the university's official documents that this student is allowed to read "
                + "(regulations, announcements, handbooks, the curriculum documents of the student's program, fee and calendar information) "
                + "and return the most relevant passages with their document title, academic year, whom each passage applies to (appliesTo), "
                + "section and pages. Use it for questions about university rules, requirements, fees, deadlines and procedures, and about "
                + "the student's curriculum: graduation requirements, total credits, program structure, required and elective courses, "
                + "internships and cooperative education. It does not return the student's own records or results: use the other tools "
                + "for those. An empty list means no relevant passage was found.");
        var query = (Map<String, Object>) ((Map<String, Object>) tool.parameterSchema().get("properties")).get("query");
        assertThat(query).containsOnlyKeys("type", "maxLength", "description");
        assertThat((String) query.get("description")).isEqualTo("What to look for, as one short question or phrase in the language of "
                + "the student's question, for example \"minimum credits to graduate\" or \"major elective courses\". Do not include the "
                + "student's name or other personal data.");
    }

    static Map<String, Object> passage(String title, String appliesTo, String section, String text) {
        var map = new LinkedHashMap<String, Object>();
        map.put("documentTitle", title);
        map.put("academicYear", "2026-2027");
        if (appliesTo != null) map.put("appliesTo", appliesTo);
        if (section != null) map.put("section", section);
        map.put("text", text);
        return map;
    }

    static String block(Map<?, ?>... passages) {
        var evidence = new AcademicEvidence("search_university_knowledge", AcademicEvidenceStatus.VERIFIED, List.of(passages),
                Instant.EPOCH, null, "q");
        return new SafeAcademicResponseFactoryImpl().groundedAnswer(List.of(evidence));
    }

    @Test
    void passageBlockPrefersTheStudentsProgram() {
        var reg = passage("Regulations", "all students", "Chapter 5", "R text");
        var dept = passage("Department Handbook", "your department", "Part 1", "D text");
        var c6 = passage("Curriculum", "your program", "Section 6", "C6 text");
        var c4a = passage("Curriculum", "your program", "Section 4", "C4a text");
        var c4b = passage("Curriculum", "your program", "Section 4", "C4b text");
        // The first program passage comes first although it is not first; the next one is not of its section.
        assertThat(block(reg, c6, dept)).isEqualTo("Here is what the university documents say:\n\n"
                + "1. Curriculum (2026-2027), for your program, Section 6\nC6 text\n\n"
                + "2. Regulations (2026-2027), for all students, Chapter 5\nR text");
        // The rest of a split section directly after it comes second.
        assertThat(block(reg, c4a, c4b)).contains("1. Curriculum (2026-2027), for your program, Section 4\nC4a text")
                .contains("2. Curriculum (2026-2027), for your program, Section 4\nC4b text").doesNotContain("R text");
        // Without a passage for "your program": the first two passages.
        assertThat(block(dept, reg)).contains("1. Department Handbook (2026-2027), for your department, Part 1")
                .contains("2. Regulations (2026-2027), for all students, Chapter 5");
        // Without appliesTo: the heading and selection of spec file 53.
        assertThat(block(passage("A", null, "S", "a"), passage("B", null, null, "b"), passage("C", null, null, "c")))
                .contains("1. A (2026-2027), S\na").contains("2. B (2026-2027)\nb");
    }
}
