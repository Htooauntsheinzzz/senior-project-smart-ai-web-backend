package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.cleaning;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextPatterns.LineClass;
import org.junit.jupiter.api.Test;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextPatterns.LineClass.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec file 48, section 16.2: every example and negative example of section 8. */
class KnowledgeTextPatternsTests {
    private static void is(LineClass expected, String... lines) {
        for (String line : lines) assertThat(KnowledgeTextPatterns.classify(line)).as(line).isEqualTo(expected);
    }

    @Test
    void examples() {
        is(MARKER_HEADING, "# Chapter 2 Registration", "## 2.1 Add and Drop", "###### Deep");
        is(KEYWORD_HEADING, "Chapter 2 Registration", "SECTION IV", "Part 1: General", "Appendix A", "หมวด 6 การลาพักการศึกษา",
                "หมวดที่ 3", "บทที่ 2 การลงทะเบียน", "ส่วนที่ 1", "ภาคผนวก ก", "Chapter IV", "chapter 4", "CHAPTER 4 Rules",
                "Annex B", "ตอนที่ 2");
        is(NUMBERED_HEADING, "2.1 Add and Drop", "3.1.1 จำนวนหน่วยกิต", "1.2.3.4.5 Five levels");
        is(CLAUSE, "Article 5. A student may withdraw.", "ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้", "มาตรา 5", "Clause 2 applies.");
        is(LIST_ITEM, "- Tuition fee", "• Item", "* Item", "– Item", "▪ Item", "● Item", "○ Item", "◦ Item", "1. First",
                "1) First", "(1) First", "a. First", "a) First", "(a) First", "iv. Fourth", "(iv) Fourth", "ก. หมวดวิชาศึกษาทั่วไป",
                "(ก) ข้อความ", "(2) Late registration");
        is(TABLE_ROW, "CS301 | Algorithms | 3");
        is(TEXT, "120 credits are required by the curriculum", "2.00 or higher in every semester", "หมวดวิชาศึกษาทั่วไป 30 หน่วยกิต",
                "ข้อมูลนักศึกษา", "Section 3 of these regulations applies to all students.", "", "Part did not apply");
    }

    @Test
    void extraCases() {
        assertThat(KnowledgeTextPatterns.classify(KnowledgeTextNormalizer.normalize("หมวดที่ ๓"))).isEqualTo(KEYWORD_HEADING);
        assertThat(KnowledgeTextPatterns.classify("หมวดที่ ๓")).isEqualTo(KEYWORD_HEADING);
        assertThat(KnowledgeTextPatterns.classify("Chapter 1 " + "x".repeat(141))).isEqualTo(TEXT);
        assertThat(KnowledgeTextPatterns.classify("Chapter 1 " + "x".repeat(140))).isEqualTo(KEYWORD_HEADING);
        assertThat(KnowledgeTextPatterns.classify("1.1 " + "y".repeat(116))).isEqualTo(TEXT);
        assertThat(KnowledgeTextPatterns.isHeading("ข้อ 13 นักศึกษา")).isFalse();
        assertThat(KnowledgeTextPatterns.startsBlock("ข้อ 13 นักศึกษา")).isTrue();
        assertThat(KnowledgeTextPatterns.isHeading("2.1 Add and Drop")).isTrue();
        assertThat(KnowledgeTextPatterns.startsBlock("plain words")).isFalse();
        assertThat(KnowledgeTextPatterns.markerLevel("### x")).isEqualTo(3);
    }
}
