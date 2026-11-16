package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.cleaning;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextCleaner;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextCleaner.RepeatedLines;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec file 48, section 16.3. */
class KnowledgeTextCleanerTests {
    private final KnowledgeTextCleaner cleaner = new KnowledgeTextCleaner(true, true, true);
    private static final String FULL = "The registration office accepts the request form until the end of";  // 67 chars

    private String clean(DocumentUnitType type, String raw) {
        return cleaner.clean(type, 1, raw, RepeatedLines.none()).text();
    }

    private static void format(String text) {
        assertThat(text).doesNotContain("\r").doesNotContain("\t").doesNotContain("  ").doesNotContain("\n\n\n")
                .doesNotStartWith("\n").doesNotEndWith("\n");
        for (String line : text.split("\n")) assertThat(line).isEqualTo(line.strip());
    }

    @Test
    void spacesDecorationDotLeadersAndBlankLines() {
        String out = clean(TEXT, "\n\n  Title   with   spaces  \n-----\n_____\n=====\n*****\n. . . .\n# ###\n"
                + "Chapter 5 Graduation ........ 12\n\n\n\nNext paragraph\n\n");
        assertThat(out).isEqualTo("Title with spaces\n# ###\nChapter 5 Graduation 12\n\nNext paragraph");
        format(out);
        assertThat(clean(TEXT, "CS301 |  Algorithms  | 3\n |  | \nA |  | B")).isEqualTo("CS301 | Algorithms | 3\nA | | B");
        // Sheets keep decoration-like lines (only normalization, trimming and blank lines apply).
        assertThat(clean(SHEET, "-----\n a  |  b ")).isEqualTo("-----\na | b");
    }

    @Test
    void pageNumberLinesAtTopAndBottomOnly() {
        for (String number : List.of("12", "๑๒", "- 12 -", "– ๕ –", "Page 12", "page 12 of 40", "หน้า 12", "หน้าที่ ๓", "หน้า 3 จาก 40", "12 / 40", "12/40")) {
            assertThat(clean(PAGE, number + "\nBody line one\nBody line two")).as("top " + number).isEqualTo("Body line one\nBody line two");
            assertThat(clean(PAGE, "Body line one\nBody line two\n" + number)).as("bottom " + number).isEqualTo("Body line one\nBody line two");
        }
        assertThat(clean(PAGE, "Intro\nSecond\n42\nFourth\nFifth")).contains("42");
        assertThat(clean(TEXT, "12\nBody")).isEqualTo("12\nBody");
    }

    @Test
    void repeatedHeadersAndFootersAreRemovedExceptOnTheirFirstPage() {
        var pages = new ArrayList<String>();
        var bodies = List.of("Registration rules apply.", "Tuition is paid online.", "Examinations are held twice.",
                "Grades are announced later.", "Graduation needs credits.");
        for (int p = 1; p <= 5; p++) pages.add("Sample University Handbook\n" + bodies.get(p - 1) + "\nPrinted copy " + p + " of 5");
        var detector = cleaner.detector();
        for (int p = 1; p <= 5; p++) detector.add(p, pages.get(p - 1));
        RepeatedLines repeated = detector.result();
        assertThat(repeated.size()).isEqualTo(2);
        assertThat(cleaner.clean(PAGE, 1, pages.get(0), repeated).text())
                .isEqualTo("Sample University Handbook\nRegistration rules apply.\nPrinted copy 1 of 5");
        for (int p = 2; p <= 5; p++)
            assertThat(cleaner.clean(PAGE, p, pages.get(p - 1), repeated).text()).isEqualTo(bodies.get(p - 1));

        var three = cleaner.detector();
        for (int p = 1; p <= 3; p++) three.add(p, pages.get(p - 1));
        assertThat(three.result().size()).isZero();

        var twoOfFive = cleaner.detector();
        for (int p = 1; p <= 5; p++) twoOfFive.add(p, (p <= 2 ? "Draft notice line\n" : "") + bodies.get(p - 1) + "\n" + bodies.get(5 - p));
        assertThat(twoOfFive.result().size()).isZero();
        assertThat(new KnowledgeTextCleaner(true, false, true).detector()).satisfies(d -> {
            for (int p = 1; p <= 5; p++) d.add(p, pages.get(p - 1));
            assertThat(d.result().size()).isZero();
        });
    }

    @Test
    void hyphenatedWords() {
        assertThat(clean(PAGE, "Late regis-\ntration costs")).isEqualTo("Late registration costs");
        assertThat(clean(PAGE, "Well-\nKnown")).isEqualTo("Well-\nKnown");
        assertThat(clean(PAGE, "Year 2024-\n2025 intake")).isEqualTo("Year 2024-\n2025 intake");
    }

    @Test
    void unwrappingEnglishKeepsParagraphsHeadingsAndClauses() {
        String page = FULL + "\n" + FULL + "\n" + FULL + "\nthe semester.\n\n" + FULL + "\nshort end.";
        assertThat(clean(PAGE, page)).isEqualTo(FULL + " " + FULL + " " + FULL + " the semester.\n\n" + FULL + " short end.");
        String blocks = FULL + "\nChapter 2 Registration\n" + FULL + "\nArticle 5. A student may withdraw.\n" + FULL
                + "\n- list item\n" + FULL + "\n2.1 Add and Drop\ncontinued text\n" + FULL;
        // A heading, a clause and a list item are never appended to the line before them; nothing is appended to a heading.
        assertThat(clean(PAGE, blocks)).isEqualTo(blocks);
        // The 70 percent rule uses the physical line: a short line ends its paragraph.
        assertThat(clean(PAGE, FULL + "\n" + FULL + "\nshort\nnext line\n" + FULL)).isEqualTo(FULL + " " + FULL + " short\nnext line\n" + FULL);
    }

    @Test
    void unwrappingThaiAddsNoSpaceUnlessTheLineEndedWithOne() {
        String a = "นักศึกษาต้องลงทะเบียนเรียนรายวิชาตามแผนการศึกษาของหลักสูตรในแต่ละภาคการศึกษาปกติ";
        String b = "และต้องชำระค่าธรรมเนียมการศึกษาภายในเวลาที่มหาวิทยาลัยกำหนดไว้ในประกาศ";
        assertThat(a.length()).isGreaterThanOrEqualTo(40);
        assertThat(clean(PAGE, a + "\n" + a + "\n" + b)).isEqualTo(a + a + b);
        assertThat(clean(PAGE, a + " \n" + a + "\n" + b)).isEqualTo(a + " " + a + b);
    }

    @Test
    void pagesThatAreNotUnwrapped() {
        assertThat(clean(PAGE, FULL + "\n" + FULL)).isEqualTo(FULL + "\n" + FULL);
        assertThat(clean(PAGE, "short one\nshort two\nshort three\nshort four")).isEqualTo("short one\nshort two\nshort three\nshort four");
        for (DocumentUnitType type : List.of(SECTION, SHEET, TEXT))
            assertThat(clean(type, FULL + "\n" + FULL + "\n" + FULL + "\nend")).isEqualTo(FULL + "\n" + FULL + "\n" + FULL + "\nend");
        var off = new KnowledgeTextCleaner(false, false, true);
        assertThat(off.clean(PAGE, 1, FULL + "\n" + FULL + "\n" + FULL + "\nend", RepeatedLines.none()).text())
                .isEqualTo(FULL + "\n" + FULL + "\n" + FULL + "\nend");
    }

    @Test
    void outputAlwaysHasTheFormat() {
        for (String raw : List.of(" a\t b \n\n\n c ", " x y\n\n", "|  | \n p ", "x . . . . y", "\n\n"))
            for (DocumentUnitType type : DocumentUnitType.values()) format(clean(type, raw));
    }
}
