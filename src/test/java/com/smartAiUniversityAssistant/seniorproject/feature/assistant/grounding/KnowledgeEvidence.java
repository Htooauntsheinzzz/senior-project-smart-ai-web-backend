package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;

import java.time.Instant;
import java.util.*;

/** Step 15A test data: knowledge evidence and passage maps in the shape AcademicAiToolResultMapper.knowledgePassage builds. */
final class KnowledgeEvidence {
    static final String TOOL = "search_university_knowledge";
    static final String ARTICLE_3 = "Chapter 2 Registration\nArticle 3. In a regular semester a student must register for at least "
            + "9 credits and at most 22 credits. In the summer session a student may register for at most 9 credits.";
    static final String ARTICLE_11 = "Chapter 5 Graduation\nArticle 11. To graduate, a student must complete at least 120 credits "
            + "required by the curriculum and have a cumulative grade point average of at least 2.00.\nArticle 12. A student must "
            + "apply for graduation within the first 30 days of the final semester.";

    private KnowledgeEvidence() {
    }

    static Map<String, Object> passage(String title, String year, String section, String pages, String text) {
        var map = new LinkedHashMap<String, Object>();
        map.put("documentTitle", title);
        map.put("category", "Regulations");
        map.put("academicYear", year);
        if (section != null) map.put("section", section);
        if (pages != null) map.put("pages", pages);
        map.put("text", text);
        return map;
    }

    static Map<String, Object> regulation(String section, String text) {
        return passage("Sample Academic Regulations 2026", "2026-2027", section, "12-13", text);
    }

    static AcademicEvidence found(String query, Map<?, ?>... passages) {
        return new AcademicEvidence(TOOL, VERIFIED, List.of(passages), Instant.EPOCH, null, query);
    }

    static AcademicEvidence empty() {
        return new AcademicEvidence(TOOL, VERIFIED_EMPTY, List.of(), Instant.EPOCH, null, "query");
    }

    static AcademicEvidence unavailable() {
        return new AcademicEvidence(TOOL, UNAVAILABLE, null, Instant.EPOCH, "KNOWLEDGE_SEARCH_UNAVAILABLE", "query");
    }

    static AcademicEvidence error() {
        return new AcademicEvidence(TOOL, ERROR, null, Instant.EPOCH, "INVALID_AI_TOOL_ARGUMENTS", null);
    }

    static AcademicEvidence database(String tool, AcademicEvidenceStatus status, Object data) {
        return new AcademicEvidence(tool, status, data, Instant.EPOCH, status == UNAVAILABLE ? "AI_TOOL_EXECUTION_FAILED" : null, null);
    }
}
