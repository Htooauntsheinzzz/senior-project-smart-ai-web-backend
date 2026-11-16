package com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding;

import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.AcademicEvidenceStatus.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.GroundingFixtures.*;
import static com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.KnowledgeEvidence.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.grounding.impl.SafeAcademicResponseFactoryImpl;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec file 53, section 20.5: the three new replies and groundedAnswer with passages. */
class KnowledgeSafeResponseTests {
    private final SafeAcademicResponseFactory responses = new SafeAcademicResponseFactoryImpl();

    @Test
    void newTexts() {
        assertThat(responses.knowledgeNotFound()).isEqualTo("I could not find information about this in the university documents I can "
                + "search. Please check with the responsible university office, or try asking in a different way.");
        assertThat(responses.knowledgeUnavailable()).isEqualTo("I can't search the university documents right now, so I won't guess. "
                + "Please try again in a few minutes.");
        assertThat(responses.knowledgeMissingEvidence()).isEqualTo("I could not look this up in the university documents, so I cannot "
                + "answer it reliably. Please try asking again.");
    }

    @Test
    void passageBlockFormat() {
        var one = regulation("Chapter 5 Graduation", ARTICLE_11);
        assertThat(responses.groundedAnswer(List.of(found("q", one)))).isEqualTo("Here is what the university documents say:\n\n"
                + "1. Sample Academic Regulations 2026 (2026-2027), Chapter 5 Graduation, pages 12-13\n" + ARTICLE_11);
        var two = passage("Handbook", "2025-2026", null, "4", "Text two.");
        var three = passage("Fees", "2026-2027", null, null, "Text three.");
        String answer = responses.groundedAnswer(List.of(found("q", one, two), found("q2", one, three)));
        assertThat(answer).contains("1. Sample Academic Regulations 2026 (2026-2027), Chapter 5 Graduation, pages 12-13")
                .contains("\n\n2. Handbook (2025-2026), page 4\nText two.").doesNotContain("Text three.").doesNotContain("3. ");
        assertThat(answer.split("Article 11\\.", -1)).hasSize(2);
        assertThat(responses.groundedAnswer(List.of(found("q", three)))).endsWith("1. Fees (2026-2027)\nText three.");
    }

    @Test
    void longTextsAreCut() {
        String words = ("word ".repeat(300)).strip();
        String block = responses.groundedAnswer(List.of(found("q", passage("T", "2026-2027", null, null, words))));
        String body = block.substring(block.indexOf("\n", block.indexOf("1. T")) + 1);
        assertThat(body).endsWith(" …");
        assertThat(body.length() - 2).isLessThanOrEqualTo(700);
        assertThat(body).doesNotEndWith("wor …");
        String thai = "นักศึกษาต้องชำระค่าธรรมเนียม".repeat(40);
        String thaiBlock = responses.groundedAnswer(List.of(found("q", passage("T", "2026-2027", null, null, thai))));
        String thaiBody = thaiBlock.substring(thaiBlock.indexOf("\n", thaiBlock.indexOf("1. T")) + 1);
        String kept = thaiBody.substring(0, thaiBody.length() - 2);
        assertThat(kept.length()).isLessThanOrEqualTo(700);
        assertThat(thai).startsWith(kept);
        // The next character is not a combining mark of the last kept one.
        assertThat(Character.getType(thai.charAt(kept.length()))).isNotEqualTo((int) Character.NON_SPACING_MARK);
    }

    @Test
    void databaseFirstThenPassages() {
        var courses = database("get_my_courses", VERIFIED, List.of(enrollment("CS301", "Data Structures", "SEC-01")));
        String answer = responses.groundedAnswer(List.of(found("q", regulation(null, ARTICLE_3)), courses));
        assertThat(answer).startsWith("Your current courses:").contains("\n\nHere is what the university documents say:");
        assertThat(responses.groundedAnswer(List.of(courses, unavailable()))).startsWith("Your current courses:")
                .endsWith("\n\n" + responses.knowledgeUnavailable());
    }

    @Test
    void emptyFailedAndInvalidSearches() {
        assertThat(responses.groundedAnswer(List.of(empty()))).isEqualTo(responses.knowledgeNotFound())
                .doesNotContain(responses.enrollmentUnavailable());
        assertThat(responses.groundedAnswer(List.of(unavailable()))).isEqualTo(responses.knowledgeUnavailable());
        assertThat(responses.groundedAnswer(List.of(empty(), unavailable()))).isEqualTo(responses.knowledgeUnavailable());
        assertThat(responses.groundedAnswer(List.of(error()))).isEqualTo(responses.knowledgeMissingEvidence());
        assertThat(responses.groundedAnswer(List.of())).isEqualTo(responses.missingEvidence());
    }
}
