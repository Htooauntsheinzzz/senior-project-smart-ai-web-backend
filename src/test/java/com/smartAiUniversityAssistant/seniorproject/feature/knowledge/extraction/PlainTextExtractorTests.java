package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.ExtractionLimits;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.PlainTextExtractor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.extraction.ExtractionTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class PlainTextExtractorTests {
    @TempDir Path dir;
    private final PlainTextExtractor extractor = new PlainTextExtractor();

    @Test
    void bomCrlfFormFeedThaiAndMyanmar() throws Exception {
        String thai = "ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้";
        String myanmar = "ကျောင်းသားများ ၁၂၀ ခရက်ဒစ်";
        byte[] content = ("﻿Line one\r\nLine two\r\n\f\f" + thai + "\r\n" + myanmar + "\n").getBytes(StandardCharsets.UTF_8);
        var units = extractor.extract(write(dir, "a.txt", content), LIMITS);
        assertThat(units).hasSize(2).allSatisfy(u -> {
            assertThat(u.unitType()).isEqualTo(DocumentUnitType.TEXT);
            assertThat(u.label()).isNull();
        });
        assertThat(units.get(0).text()).isEqualTo("Line one\nLine two");
        assertThat(units.get(1).text()).isEqualTo(thai + "\n" + myanmar);
    }

    @Test
    void longTextIsSplitAtLineEndsAndLongLinesAreCut() throws Exception {
        var text = new StringBuilder();
        for (int i = 0; i < 50; i++) text.append("Line ").append(i).append(' ').append("x".repeat(95)).append('\n');
        var units = extractor.extract(write(dir, "b.txt", text.toString().getBytes(StandardCharsets.UTF_8)),
                new ExtractionLimits(100, 5_000_000, 2000));
        assertThat(units.size()).isGreaterThan(1);
        assertThat(units).allSatisfy(u -> assertThat(u.text()).doesNotStartWith("\n").doesNotEndWith("\n"));
        assertThat(units.get(0).text().split("\n")).allSatisfy(line -> assertThat(line).startsWith("Line "));
        String longLine = "😀".repeat(1500);  // 3000 chars of surrogate pairs on one line
        var cut = extractor.extract(write(dir, "c.txt", longLine.getBytes(StandardCharsets.UTF_8)), new ExtractionLimits(100, 5_000_000, 2001));
        assertThat(cut).hasSize(2);
        assertThat(cut.get(0).text().length()).isEqualTo(2000);
        assertThat(String.join("", texts(cut))).isEqualTo(longLine);
    }

    @Test
    void invalidUtf8AndLimits() throws Exception {
        assertThatThrownBy(() -> extractor.extract(write(dir, "bad.txt", new byte[] {'a', (byte) 0xC3, (byte) 0x28}), LIMITS))
                .isInstanceOf(java.io.IOException.class);
        failsWith("DOCUMENT_TEXT_TOO_LARGE", () -> extractor.extract(write(dir, "big.txt", "y".repeat(20_001).getBytes(StandardCharsets.UTF_8)),
                new ExtractionLimits(100, 20_000, 2000)));
        failsWith("DOCUMENT_TOO_MANY_PAGES", () -> extractor.extract(write(dir, "ff.txt", "a\fb\fc".getBytes(StandardCharsets.UTF_8)),
                new ExtractionLimits(2, 20_000, 2000)));
    }
}
