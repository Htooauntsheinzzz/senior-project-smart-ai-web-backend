package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import java.text.Normalizer;

/**
 * Character normalization of document text and search questions (spec file 48, section 7), in this order: NFC, line
 * separators, spaces, zero width space, invisible characters, Latin ligatures, Thai SARA AM, Thai glyph variants, Thai
 * digits (optional), hyphens, remaining Private Use characters, control characters. No NFKC (it would split SARA AM and
 * rewrite characters such as m²). U+FFFD is kept. Pure function, idempotent.
 */
public final class KnowledgeTextNormalizer {
    private KnowledgeTextNormalizer() {}

    /** The normalized text and how many Private Use characters step 11 removed (for the unreadable-text check). */
    public record Result(String text, int removedPrivateUse) {}

    /**
     * Shifted Thai vowels and tone marks of Windows Thai fonts (U+F700 to U+F71A) to the standard characters. Source:
     * HarfBuzz, src/hb-ot-shaper-thai.cc, the Windows PUA column of its SD, SDL, SL and RD shaping tables
     * (https://github.com/harfbuzz/harfbuzz/blob/main/src/hb-ot-shaper-thai.cc). Index = code point − U+F700.
     */
    private static final char[] THAI_PUA = {
            'ฐ', // F700 THO THAN
            'ิ', // F701 SARA I
            'ี', // F702 SARA II
            'ึ', // F703 SARA UE
            'ื', // F704 SARA UEE
            '่', // F705 MAI EK
            '้', // F706 MAI THO
            '๊', // F707 MAI TRI
            '๋', // F708 MAI CHATTAWA
            '์', // F709 THANTHAKHAT
            '่', // F70A MAI EK
            '้', // F70B MAI THO
            '๊', // F70C MAI TRI
            '๋', // F70D MAI CHATTAWA
            '์', // F70E THANTHAKHAT
            'ญ', // F70F YO YING
            'ั', // F710 MAI HAN-AKAT
            'ํ', // F711 NIKHAHIT
            '็', // F712 MAITAIKHU
            '่', // F713 MAI EK
            '้', // F714 MAI THO
            '๊', // F715 MAI TRI
            '๋', // F716 MAI CHATTAWA
            '์', // F717 THANTHAKHAT
            'ุ', // F718 SARA U
            'ู', // F719 SARA UU
            'ฺ', // F71A PHINTHU
    };

    /** Same as normalize(text, true) (the default of thai-digits-to-ascii). */
    public static String normalize(String text) { return normalize(text, true); }

    public static String normalize(String text, boolean thaiDigitsToAscii) {
        return normalizeWithStats(text, thaiDigitsToAscii).text();
    }

    public static Result normalizeWithStats(String text, boolean thaiDigitsToAscii) {
        Result first = once(text, thaiDigitsToAscii);
        // A removal in steps 11 or 12 can bring two characters together that an earlier step combines; repeat until stable.
        String current = first.text();
        for (int i = 0; i < 3; i++) {
            String next = once(current, thaiDigitsToAscii).text();
            if (next.equals(current)) break;
            current = next;
        }
        return new Result(current, first.removedPrivateUse());
    }

    private static Result once(String input, boolean thaiDigitsToAscii) {
        // 1 NFC
        String s = Normalizer.normalize(input, Normalizer.Form.NFC);
        var b = new StringBuilder(s.length());
        // 2 line separators, 3 spaces
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == ' ' || c == '\u0085' || c == '\u000B') b.append('\n');
            else if (c == '\t' || c == ' ' || c == ' ' || (c >= ' ' && c <= ' ') || c == ' '
                    || c == ' ' || c == '　') b.append(' ');
            else b.append(c);
        }
        // 4 zero width space, 5 invisible characters, 6 ligatures
        s = b.toString();
        b.setLength(0);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '​') {
                if (i > 0 && i + 1 < s.length() && latinOrDigit(s.charAt(i - 1)) && latinOrDigit(s.charAt(i + 1))) b.append(' ');
            } else if (invisible(c)) {
                // removed; U+200C and U+200D are kept
            } else if (c >= 'ﬀ' && c <= 'ﬆ') {
                b.append(switch (c) {
                    case 'ﬀ' -> "ff";
                    case 'ﬁ' -> "fi";
                    case 'ﬂ' -> "fl";
                    case 'ﬃ' -> "ffi";
                    case 'ﬄ' -> "ffl";
                    default -> "st";
                });
            } else b.append(c);
        }
        // 8 Thai glyph variants (before 7, so a mapped NIKHAHIT also forms SARA AM; both only touch Thai marks)
        s = b.toString();
        b.setLength(0);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c >= '' && c <= '' ? THAI_PUA[c - ''] : c);
        }
        // 7 SARA AM: NIKHAHIT + SARA AA with an optional tone mark before or between them
        s = b.toString().replaceAll("([่-๋]?)ํ([่-๋]?)า", "$1$2ำ");
        b.setLength(0);
        // 9 Thai digits, 10 hyphens, 11 Private Use, 12 control characters
        int removed = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (thaiDigitsToAscii && c >= '๐' && c <= '๙') b.append((char) ('0' + (c - '๐')));
            else if (c == '‐' || c == '‑') b.append('-');
            else if ((c == '‒' || c == '–' || c == '−') && i > 0 && i + 1 < s.length()
                    && asciiDigit(s.charAt(i - 1)) && asciiDigit(s.charAt(i + 1))) b.append('-');
            else if (c >= '' && c <= '') {
                if (b.isEmpty() || b.charAt(b.length() - 1) == '\n') b.append('-');
                else removed++;
            } else if (c != '\n' && Character.getType(c) == Character.CONTROL) {
                // removed
            } else b.append(c);
        }
        return new Result(b.toString(), removed);
    }

    private static boolean invisible(char c) {
        return c == '­' || c == '‎' || c == '‏' || (c >= '‪' && c <= '‮')
                || (c >= '⁠' && c <= '⁤') || (c >= '⁦' && c <= '⁩') || c == '﻿';
    }

    private static boolean latinOrDigit(char c) {
        return Character.isDigit(c) || Character.isLetter(c) && Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN;
    }

    private static boolean asciiDigit(char c) { return c >= '0' && c <= '9'; }
}
