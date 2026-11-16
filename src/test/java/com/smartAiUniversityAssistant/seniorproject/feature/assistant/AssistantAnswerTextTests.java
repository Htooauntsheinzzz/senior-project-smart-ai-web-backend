package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantAnswerText;
import org.junit.jupiter.api.Test;

/** Spec file 59 (answer style, D85): Markdown syntax is removed from a model answer, the words are not changed. */
class AssistantAnswerTextTests {
    @Test
    void markdownEmphasisAndBulletsBecomePlainText() {
        String answer = """
                According to **academic-calendar-2** (Academic year 2026-2027, Page 2), the term break for Semester 2 is:

                * **March 1 – 5, 2027**
                """;
        assertThat(AssistantAnswerText.plain(answer)).isEqualTo("""
                According to academic-calendar-2 (Academic year 2026-2027, Page 2), the term break for Semester 2 is:

                - March 1 – 5, 2027""");
        assertThat(AssistantAnswerText.plain("The fee is ***500 baht*** and *must* be paid __before__ _Friday_."))
                .isEqualTo("The fee is 500 baht and must be paid before Friday.");
        assertThat(AssistantAnswerText.plain("ค่าธรรมเนียม**500 บาท**ต่อภาคเรียน")).isEqualTo("ค่าธรรมเนียม500 บาทต่อภาคเรียน");
        assertThat(AssistantAnswerText.plain("+ one\n  * two\n• three")).isEqualTo("- one\n  - two\n- three");
    }

    @Test
    void headingsQuotesCodeLinksAndRulesAreRemoved() {
        String answer = """
                ## Registration ##

                > Register in `MyRSU` before the deadline.

                ---

                ```text
                Step 1
                ```
                See [the calendar](https://example.test/calendar "Calendar") or ~~the old page~~.
                Use \\*this\\* form.
                """;
        assertThat(AssistantAnswerText.plain(answer)).isEqualTo("""
                Registration

                Register in MyRSU before the deadline.

                Step 1
                See the calendar (https://example.test/calendar) or the old page.
                Use *this* form.""");
    }

    @Test
    void plainAnswersAreNotChanged() {
        for (String answer : new String[] {
                "You need 120 credits. The late fee is 500 baht.",
                "1. CS301 Data Structures\n2. CS302 Algorithms",
                "- Monday 13:00-15:45 in room 11-500\n- Friday 09:00-11:45",
                "The file academic_calendar_2 and section CS301_A apply.",
                "5*3*2 is 30, and a * b * c is a product.",
                "Email registrar@example.test (office hours 9 to 16).",
                "Paragraph one.\n\nParagraph two."})
            assertThat(AssistantAnswerText.plain(answer)).isEqualTo(answer);
        assertThat(AssistantAnswerText.plain("  Hello.\r\n\r\n\r\n\r\nBye.  \n")).isEqualTo("Hello.\n\nBye.");
        assertThat(AssistantAnswerText.plain(null)).isEmpty();
    }
}
