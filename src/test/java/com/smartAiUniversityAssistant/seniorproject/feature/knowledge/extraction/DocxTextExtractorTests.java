package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeRealFiles.Docx;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.ExtractionLimits;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.DocxTextExtractor;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction.ExtractionTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class DocxTextExtractorTests {
    @TempDir Path dir;
    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    void levelOneHeadingsStartSectionsWithMarkersTablesListsAndThai() throws Exception {
        byte[] docx = new Docx()
                .headerAndFooter("HEADER TEXT", "FOOTER TEXT")
                .paragraph("Preface before any heading.")
                .heading("Heading1", "Chapter 1 General")
                .paragraph("Body of chapter one.")
                .heading("Heading2", "1.1 Scope")
                .listItem("First item")
                .listItem("Second item")
                .paragraph("After the list.")
                .table(new String[] {"Code", "Credits"}, new String[] {"CS301", "3"})
                .heading("MyChapter", "Chapter 2 การลงทะเบียน")
                .paragraph("นักศึกษาต้องลงทะเบียนเรียน")
                .trackedChanges("Kept ", "INSERTED", "DELETED TEXT")
                .bytes();
        var units = extractor.extract(write(dir, "a.docx", docx), LIMITS);
        assertThat(units).extracting(u -> u.unitType()).containsOnly(DocumentUnitType.SECTION);
        assertThat(units).extracting(u -> u.label()).containsExactly(null, "Chapter 1 General", "Chapter 2 การลงทะเบียน");
        assertThat(units.get(0).text()).isEqualTo("Preface before any heading.");
        assertThat(units.get(1).text()).isEqualTo("""
                # Chapter 1 General

                Body of chapter one.

                ## 1.1 Scope

                - First item
                - Second item

                After the list.

                Code | Credits
                CS301 | 3""");
        assertThat(units.get(2).text()).startsWith("# Chapter 2 การลงทะเบียน\n\nนักศึกษาต้องลงทะเบียนเรียน")
                .contains("Kept INSERTED").doesNotContain("DELETED");
        String all = String.join("\n", texts(units));
        assertThat(all).doesNotContain("HEADER TEXT").doesNotContain("FOOTER TEXT");
    }

    @Test
    void titleStyleIsALevelOneHeading() throws Exception {
        var units = extractor.extract(write(dir, "t.docx", new Docx().heading("Title", "Handbook").paragraph("Text").bytes()), LIMITS);
        assertThat(units).singleElement().satisfies(u -> {
            assertThat(u.label()).isEqualTo("Handbook");
            assertThat(u.text()).isEqualTo("# Handbook\n\nText");
        });
    }

    @Test
    void longSectionIsSplitWithTheSameLabel() throws Exception {
        var docx = new Docx().heading("Heading1", "Chapter 9 Long");
        for (int i = 0; i < 30; i++) docx.paragraph("Paragraph " + i + " " + "w".repeat(150));
        var units = extractor.extract(write(dir, "l.docx", docx.bytes()), new ExtractionLimits(2000, 5_000_000, 2000));
        assertThat(units.size()).isGreaterThan(1);
        assertThat(units).allSatisfy(u -> {
            assertThat(u.label()).isEqualTo("Chapter 9 Long");
            assertThat(u.text().length()).isLessThan(2000 + 200);
        });
    }
}
