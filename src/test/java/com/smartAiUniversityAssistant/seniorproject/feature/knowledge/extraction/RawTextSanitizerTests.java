package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec file 47, section 12: only four changes are made to raw text. */
class RawTextSanitizerTests {
    @Test
    void onlyTheFourChanges() {
        assertThat(ExtractTextStage.sanitize("a\u0000b")).isEqualTo("ab");
        assertThat(ExtractTextStage.sanitize("x\uD83Dy")).isEqualTo("x�y");
        assertThat(ExtractTextStage.sanitize("x\uDE00y")).isEqualTo("x�y");
        assertThat(ExtractTextStage.sanitize("ok 😀")).isEqualTo("ok 😀");
        assertThat(ExtractTextStage.sanitize("a\r\nb\rc\nd")).isEqualTo("a\nb\nc\nd");
        assertThat(ExtractTextStage.sanitize("a\fb")).isEqualTo("ab");
        String untouched = "  Leading and trailing  \t tabs, ﬁ ligature, ๑๒๐, é, ​ zero width  ";
        assertThat(ExtractTextStage.sanitize(untouched)).isEqualTo(untouched);
    }
}
