package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeIngestionException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.nio.file.*;
import java.util.List;
import org.assertj.core.api.ThrowableAssert;

import static org.assertj.core.api.Assertions.*;

final class ExtractionTestSupport {
    static final ExtractionLimits LIMITS = new ExtractionLimits(2000, 5_000_000, 20_000);
    private ExtractionTestSupport() {}

    static Path write(Path directory, String name, byte[] content) throws Exception {
        Path file = directory.resolve(name);
        Files.write(file, content);
        return file;
    }

    static void failsWith(String code, ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(KnowledgeIngestionException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    static List<String> texts(List<ExtractedUnit> units) { return units.stream().map(ExtractedUnit::text).toList(); }
}
