package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeExtractionProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeRealFiles;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.ExtractionLimits;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.PdfTextExtractor;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction.ExtractionTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class PdfTextExtractorTests {
    @TempDir Path dir;
    private final PdfTextExtractor extractor = new PdfTextExtractor(
            new KnowledgeExtractionProperties(2000, 5_000_000, 20_000, Duration.ofMinutes(5), true, ""));

    @Test
    void onePageUnitPerPageInOrder() throws Exception {
        var units = extractor.extract(write(dir, "a.pdf", KnowledgeRealFiles.pdf("First page text", "Second page text\nline two", "Third page")), LIMITS);
        assertThat(units).hasSize(3).allSatisfy(u -> {
            assertThat(u.unitType()).isEqualTo(DocumentUnitType.PAGE);
            assertThat(u.label()).isNull();
        });
        assertThat(units.get(0).text()).contains("First page text");
        assertThat(units.get(1).text()).contains("Second page text\nline two");
        assertThat(units.get(2).text()).contains("Third page").doesNotContain("First");
        assertThat(extractor.supports("pdf")).isTrue();
        assertThat(extractor.supports("docx")).isFalse();
    }

    @Test
    void emptyAndDrawingOnlyPagesKeepTheNumbering() throws Exception {
        var units = extractor.extract(write(dir, "a.pdf", KnowledgeRealFiles.pdf("One", "", null, "Four")), LIMITS);
        assertThat(units).hasSize(4);
        assertThat(units.get(1).text()).isBlank();
        assertThat(units.get(2).text()).isBlank();
        assertThat(units.get(3).text()).contains("Four");
    }

    @Test
    void pdfWithoutTextIsEmptyOnEveryPage() throws Exception {
        var units = extractor.extract(write(dir, "a.pdf", KnowledgeRealFiles.pdf(new String[] {null, ""})), LIMITS);
        // The stage turns "no unit with a letter or digit" into DOCUMENT_TEXT_EMPTY.
        assertThat(units).hasSize(2).allSatisfy(u -> assertThat(u.text()).isBlank());
    }

    @Test
    void protectedPdfsAreRejected() throws Exception {
        failsWith("DOCUMENT_ENCRYPTED", () -> extractor.extract(write(dir, "p.pdf", KnowledgeRealFiles.passwordPdf("Secret")), LIMITS));
        failsWith("DOCUMENT_ENCRYPTED", () -> extractor.extract(write(dir, "n.pdf", KnowledgeRealFiles.noExtractionPdf("Secret")), LIMITS));
    }

    @Test
    void limitsAreEnforced() throws Exception {
        failsWith("DOCUMENT_TOO_MANY_PAGES", () -> extractor.extract(write(dir, "a.pdf", KnowledgeRealFiles.pdf("a", "b", "c")),
                new ExtractionLimits(2, 5_000_000, 20_000)));
        failsWith("DOCUMENT_TEXT_TOO_LARGE", () -> extractor.extract(write(dir, "b.pdf",
                KnowledgeRealFiles.pdf("x".repeat(80), "y".repeat(80))), new ExtractionLimits(10, 100, 20_000)));
    }

    @Test
    void damagedPdfThrowsALibraryError() throws Exception {
        Path file = write(dir, "d.pdf", KnowledgeTestFiles.pdf("not really a pdf"));
        assertThatThrownBy(() -> extractor.extract(file, LIMITS)).isInstanceOf(IOException.class);
    }
}
