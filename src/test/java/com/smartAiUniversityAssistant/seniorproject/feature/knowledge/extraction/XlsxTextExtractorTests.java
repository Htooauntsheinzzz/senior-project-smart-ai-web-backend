package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeRealFiles;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.XlsxTextExtractor;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction.ExtractionTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class XlsxTextExtractorTests {
    @TempDir Path dir;
    private final XlsxTextExtractor extractor = new XlsxTextExtractor();

    @Test
    void visibleSheetsWithDisplayedValuesGapsAndCachedFormulas() throws Exception {
        var units = extractor.extract(write(dir, "a.xlsx", KnowledgeRealFiles.xlsx()), LIMITS);
        assertThat(units).extracting(u -> u.label()).containsExactly("Courses", "Calendar");
        assertThat(units).extracting(u -> u.unitType()).containsOnly(DocumentUnitType.SHEET);
        assertThat(units.get(0).text()).isEqualTo("""
                Code | Title | Credits
                CS301 | Algorithms | 3
                CS302 |  | 4
                Total |  | 7""");
        assertThat(units.get(1).text()).isEqualTo("Semester start | 2026-06-15\nLate fee | 1500.5");
        assertThat(String.join("\n", texts(units))).doesNotContain("HIDDEN");
    }
}
