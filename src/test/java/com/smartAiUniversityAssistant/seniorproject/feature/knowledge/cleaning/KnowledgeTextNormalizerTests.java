package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.cleaning;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextNormalizer;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec file 48, section 16.1. Inputs use escapes so the exact code points are visible. */
class KnowledgeTextNormalizerTests {
    private static String n(String s) { return KnowledgeTextNormalizer.normalize(s, true); }

    @Test
    void table() {
        assertThat(n("é")).isEqualTo("é");
        assertThat(n("m²")).isEqualTo("m²");
        assertThat(n("ﬁnal oﬃce")).isEqualTo("final office");
        assertThat(n("A B")).isEqualTo("A B");
        assertThat(n("A\tB")).isEqualTo("A B");
        assertThat(n("ab​cd")).isEqualTo("ab cd");
        assertThat(n("นัก​ศึกษา")).isEqualTo("นักศึกษา");
        assertThat(n("co­operate")).isEqualTo("cooperate");
        assertThat(n("﻿ab‎c")).isEqualTo("abc");
        assertThat(n("a‌b‍c")).isEqualTo("a‌b‍c");
        assertThat(n("ทํา")).isEqualTo("ทำ");
        assertThat(n("นํ้า")).isEqualTo("น้ำ");
        assertThat(n("น้ํา")).isEqualTo("น้ำ");
        assertThat(n("๑๒๐ หน่วยกิต")).isEqualTo("120 หน่วยกิต");
        assertThat(KnowledgeTextNormalizer.normalize("๑๒๐", false)).isEqualTo("๑๒๐");
        String myanmar = "ကျောင်းသား ၁၂၀ ခရက်ဒစ်";
        assertThat(n(myanmar)).isEqualTo(myanmar);
        assertThat(n("2024–2025")).isEqualTo("2024-2025");
        assertThat(n("09:00–12:00")).isEqualTo("09:00-12:00");
        assertThat(n("Monday – Friday")).isEqualTo("Monday – Friday");
        assertThat(n("well‐known")).isEqualTo("well-known");
        assertThat(n(" Tuition fee\n Late fee")).isEqualTo("- Tuition fee\n- Late fee");
        assertThat(n("inside")).isEqualTo("inside");
        var controls = new StringBuilder("a");
        for (char c = 0; c < 0x20; c++) controls.append(c);
        controls.append("b");
        assertThat(n(controls.toString())).isEqualTo("a \n\nb"); // tab becomes a space (step 3), U+000B a line break (step 2)
        assertThat(n("x y z\u0085w\u000Bv")).isEqualTo("x\ny\nz\nw\nv");
        // Thai glyph variants (HarfBuzz table): shifted SARA I and a low MAI EK.
        assertThat(n("ป")).isEqualTo("ปิ");
        assertThat(n("ป")).isEqualTo("ป่");
        assertThat(n("")).isEqualTo("ฐ");
    }

    @Test
    void idempotent() {
        for (String input : List.of("é", "ﬁnal", "A B\tC", "ab​cd", "นํ้า", "๑๒",
                "2024–2025", " x", "aํา", "co­op", "ป", "x\u0001y"))
            assertThat(n(n(input))).as(input).isEqualTo(n(input));
    }

    @Test
    void reportsRemovedPrivateUseCharacters() {
        assertThat(KnowledgeTextNormalizer.normalizeWithStats(" ab", true).removedPrivateUse()).isEqualTo(2);
    }
}
