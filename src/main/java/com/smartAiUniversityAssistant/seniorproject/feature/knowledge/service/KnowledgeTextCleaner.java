package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextPatterns.LineClass;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Line and page rules of the CLEAN stage (spec file 48, sections 9 to 12). Pure functions over strings: no database, no
 * I/O. Meaning is never changed; only layout, invisible characters and repeated page furniture are touched. The output
 * always has the format of section 12. Raise VERSION whenever a rule changes (every document then needs a FULL run).
 */
public final class KnowledgeTextCleaner {
    public static final int VERSION = 1;
    /** Lines at the top and at the bottom of a page that can be page numbers or running headers (10.1, 10.2). */
    static final int ZONE_LINES = 2;
    /** A repeated line is on at least this share of the pages with text (10.2). */
    static final double REPEATED_SHARE = 0.6;
    /** ... and on at least this many pages (10.2). */
    static final int REPEATED_MIN_PAGES = 3;
    /** Repeated-line detection needs at least this many pages with text (10.2). */
    static final int REPEATED_MIN_TEXT_PAGES = 4;
    /** A repeated-line key needs at least this many letters (10.2). */
    static final int KEY_MIN_LETTERS = 3;
    /** Unwrapping: the reference length is the 90th percentile of the TEXT line lengths of a page (10.4). */
    static final double REFERENCE_PERCENTILE = 0.9;
    /** Unwrapping needs at least this many TEXT lines on the page (10.4). */
    static final int UNWRAP_MIN_TEXT_LINES = 3;
    /** Pages whose reference length is shorter are usually tables or lists and are not unwrapped (10.4). */
    static final int UNWRAP_MIN_REFERENCE = 40;
    /** A line at least this share of the reference length continues on the next line (10.4). */
    static final double UNWRAP_FULL_LINE_SHARE = 0.7;

    private static final String D = "[0-9\u0E50-\u0E59]{1,4}";
    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^(?:" + D + "|[-\u2013\u2014]\\s*" + D + "\\s*[-\u2013\u2014]|(?i:page)\\s*" + D + "(?:\\s*(?i:of)\\s*" + D + ")?"
            + "|หน้า(?:ที่)?\\s*" + D + "(?:\\s*จาก\\s*" + D + ")?|" + D + "\\s*/\\s*" + D + ")$");
    private static final Pattern DOT_LEADER = Pattern.compile("\\.(?: ?\\.){3,}");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("\\s\\|\\s");
    private static final Pattern DIGIT_RUN = Pattern.compile("[0-9\u0E50-\u0E59]+");

    private final boolean unwrapLines;
    private final boolean removeRepeatedLines;
    private final boolean thaiDigitsToAscii;

    public KnowledgeTextCleaner(boolean unwrapLines, boolean removeRepeatedLines, boolean thaiDigitsToAscii) {
        this.unwrapLines = unwrapLines;
        this.removeRepeatedLines = removeRepeatedLines;
        this.thaiDigitsToAscii = thaiDigitsToAscii;
    }

    public boolean removeRepeatedLines() { return removeRepeatedLines; }

    /** Counters for the log line of the stage. */
    public static final class Stats {
        public long pageNumberLines, repeatedLines, joinedLines, hyphenJoins, removedPrivateUse, replacementChars, nonSpaceChars;
        public void add(Stats o) {
            pageNumberLines += o.pageNumberLines; repeatedLines += o.repeatedLines; joinedLines += o.joinedLines;
            hyphenJoins += o.hyphenJoins; removedPrivateUse += o.removedPrivateUse; replacementChars += o.replacementChars;
            nonSpaceChars += o.nonSpaceChars;
        }
    }

    public record Cleaned(String text, Stats stats) {}

    /** One line with whether it ended with a space before trimming (needed by unwrapping). */
    private record Line(String text, boolean endedWithSpace, int physicalLength) {}

    // ---------- pass 1: repeated headers and footers (10.2) ----------

    /** Keys of the repeated lines per zone and the first page on which each appears. Keeps a few keys per page only. */
    public static final class RepeatedLines {
        private final Map<String, Integer> top;
        private final Map<String, Integer> bottom;
        RepeatedLines(Map<String, Integer> top, Map<String, Integer> bottom) { this.top = top; this.bottom = bottom; }
        public static RepeatedLines none() { return new RepeatedLines(Map.of(), Map.of()); }
        public int size() { return top.size() + bottom.size(); }
    }

    public final class RepeatedLineDetector {
        private final Map<String, Integer> topCount = new HashMap<>(), bottomCount = new HashMap<>();
        private final Map<String, Integer> topFirst = new HashMap<>(), bottomFirst = new HashMap<>();
        private int pagesWithText;

        /** Adds one PAGE unit (its raw text), in page order. */
        public void add(int pageNumber, String raw) {
            List<String> lines = pageNumbersRemoved(prepared(normalized(raw).text(), true), null);
            List<String> nonEmpty = lines.stream().filter(l -> !l.isEmpty()).toList();
            if (nonEmpty.stream().noneMatch(KnowledgeTextCleaner::hasLetterOrDigit)) return;
            pagesWithText++;
            count(zoneKeys(nonEmpty, true), topCount, topFirst, pageNumber);
            count(zoneKeys(nonEmpty, false), bottomCount, bottomFirst, pageNumber);
        }

        private void count(Set<String> keys, Map<String, Integer> counts, Map<String, Integer> first, int page) {
            for (String key : keys) {
                counts.merge(key, 1, Integer::sum);
                first.putIfAbsent(key, page);
            }
        }

        public RepeatedLines result() {
            if (!removeRepeatedLines || pagesWithText < REPEATED_MIN_TEXT_PAGES) return RepeatedLines.none();
            return new RepeatedLines(repeated(topCount, topFirst), repeated(bottomCount, bottomFirst));
        }

        private Map<String, Integer> repeated(Map<String, Integer> counts, Map<String, Integer> first) {
            var result = new HashMap<String, Integer>();
            counts.forEach((key, n) -> {
                if (n >= REPEATED_MIN_PAGES && n >= REPEATED_SHARE * pagesWithText) result.put(key, first.get(key));
            });
            return result;
        }
    }

    public RepeatedLineDetector detector() { return new RepeatedLineDetector(); }

    private static Set<String> zoneKeys(List<String> nonEmpty, boolean top) {
        var keys = new LinkedHashSet<String>();
        int n = nonEmpty.size();
        for (int i = 0; i < Math.min(ZONE_LINES, n); i++) {
            String key = key(nonEmpty.get(top ? i : n - 1 - i));
            if (key != null) keys.add(key);
        }
        return keys;
    }

    /** Digit runs replaced by #; null when the line has fewer than three letters. */
    static String key(String line) {
        String key = DIGIT_RUN.matcher(line).replaceAll("#");
        return key.codePoints().filter(Character::isLetter).count() < KEY_MIN_LETTERS ? null : key;
    }

    // ---------- pass 2 ----------

    /** Cleans one unit, always starting from its raw text. */
    public Cleaned clean(DocumentUnitType type, int pageNumber, String raw, RepeatedLines repeated) {
        var stats = new Stats();
        var normalized = normalized(raw);
        stats.removedPrivateUse = normalized.removedPrivateUse();
        String text = normalized.text();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD') stats.replacementChars++;
            if (!Character.isWhitespace(c)) stats.nonSpaceChars++;
        }
        boolean page = type == DocumentUnitType.PAGE;
        List<Line> lines = lines(text, type != DocumentUnitType.SHEET);
        if (page) {
            lines = pageNumbers(lines, stats);
            if (repeated != null && repeated.size() > 0) lines = removeRepeated(lines, pageNumber, repeated, stats);
            lines = hyphenJoins(lines, stats);
            if (unwrapLines) lines = unwrap(lines, stats);
        }
        return new Cleaned(blankLines(lines), stats);
    }

    KnowledgeTextNormalizer.Result normalized(String raw) {
        return KnowledgeTextNormalizer.normalizeWithStats(raw, thaiDigitsToAscii);
    }

    /** Section 9 steps 1 to 3 plus table cells; blank lines stay for now (they separate paragraphs). */
    private List<Line> lines(String text, boolean decorations) {
        var result = new ArrayList<Line>();
        for (String raw : text.split("\n", -1)) {
            boolean endedWithSpace = !raw.isEmpty() && raw.charAt(raw.length() - 1) == ' ';
            if (TABLE_SEPARATOR.matcher(" " + raw + " ").find()) {
                String row = table(raw);
                if (row != null) result.add(new Line(row, endedWithSpace, row.length()));
                continue;
            }
            String line = collapse(raw);
            if (decorations) {
                if (decoration(line)) continue;
                line = collapse(DOT_LEADER.matcher(line).replaceAll(" "));
            }
            result.add(new Line(line, endedWithSpace, line.length()));
        }
        return result;
    }

    /** Pass-1 form of the same preparation, as plain strings. */
    private List<String> prepared(String text, boolean decorations) {
        return lines(text, decorations).stream().map(Line::text).toList();
    }

    private static String collapse(String line) {
        return line.replaceAll(" {2,}", " ").strip();
    }

    /** Only three or more punctuation or symbol characters (spaces between them allowed); never a marker heading. */
    static boolean decoration(String line) {
        if (line.isEmpty() || KnowledgeTextPatterns.markerLevel(line) > 0) return false;
        int count = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') continue;
            if (!punctuationOrSymbol(cp)) return false;
            count++;
        }
        return count >= 3;
    }

    private static boolean punctuationOrSymbol(int cp) {
        return switch (Character.getType(cp)) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL,
                 Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    /**
     * Cells trimmed and joined with " | " again; a row whose cells are all empty is removed (null). An empty cell between
     * two others gives "a | | b", so the line keeps the format rule of section 12 (no two spaces in a row).
     */
    private static String table(String line) {
        String[] cells = line.strip().split("\\s*\\|\\s*", -1);
        boolean any = false;
        for (int i = 0; i < cells.length; i++) {
            cells[i] = cells[i].strip();
            any |= !cells[i].isEmpty();
        }
        if (!any) return null;
        return collapse(String.join(" | ", cells));
    }

    /** 10.1: page-number lines among the first two and the last two non-empty lines. */
    private List<Line> pageNumbers(List<Line> lines, Stats stats) {
        var texts = lines.stream().map(Line::text).toList();
        var removed = new ArrayList<Integer>();
        var kept = pageNumbersRemoved(texts, removed);
        if (stats != null) stats.pageNumberLines += removed.size();
        var result = new ArrayList<Line>();
        for (int i = 0; i < lines.size(); i++) if (!removed.contains(i)) result.add(lines.get(i));
        assert result.size() == kept.size();
        return result;
    }

    private static List<String> pageNumbersRemoved(List<String> lines, List<Integer> removedIndexes) {
        var candidates = zoneIndexes(lines);
        var result = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            if (candidates.contains(i) && PAGE_NUMBER.matcher(lines.get(i)).matches()) {
                if (removedIndexes != null) removedIndexes.add(i);
            } else result.add(lines.get(i));
        }
        return result;
    }

    /** Indexes of the first two and the last two non-empty lines. */
    private static Set<Integer> zoneIndexes(List<String> lines) {
        var nonEmpty = new ArrayList<Integer>();
        for (int i = 0; i < lines.size(); i++) if (!lines.get(i).isEmpty()) nonEmpty.add(i);
        var zone = new HashSet<Integer>();
        for (int i = 0; i < Math.min(ZONE_LINES, nonEmpty.size()); i++) {
            zone.add(nonEmpty.get(i));
            zone.add(nonEmpty.get(nonEmpty.size() - 1 - i));
        }
        return zone;
    }

    /** 10.2: a repeated line is removed from its zone on every page except the first page on which it appears. */
    private List<Line> removeRepeated(List<Line> lines, int pageNumber, RepeatedLines repeated, Stats stats) {
        var nonEmpty = new ArrayList<Integer>();
        for (int i = 0; i < lines.size(); i++) if (!lines.get(i).text().isEmpty()) nonEmpty.add(i);
        var remove = new HashSet<Integer>();
        int n = nonEmpty.size();
        for (int z = 0; z < Math.min(ZONE_LINES, n); z++) {
            check(lines, nonEmpty.get(z), repeated.top, pageNumber, remove);
            check(lines, nonEmpty.get(n - 1 - z), repeated.bottom, pageNumber, remove);
        }
        stats.repeatedLines += remove.size();
        var result = new ArrayList<Line>();
        for (int i = 0; i < lines.size(); i++) if (!remove.contains(i)) result.add(lines.get(i));
        return result;
    }

    private static void check(List<Line> lines, int index, Map<String, Integer> zone, int pageNumber, Set<Integer> remove) {
        String key = key(lines.get(index).text());
        Integer first = key == null ? null : zone.get(key);
        if (first != null && first != pageNumber) remove.add(index);
    }

    /** 10.3: "regis-" + "tration" → "registration" (lower-case Latin on both sides, next line is TEXT). */
    private static List<Line> hyphenJoins(List<Line> lines, Stats stats) {
        var result = new ArrayList<Line>();
        for (Line line : lines) {
            Line previous = result.isEmpty() ? null : result.get(result.size() - 1);
            if (previous != null && endsWithLowerHyphen(previous.text()) && !line.text().isEmpty()
                    && isLowerLatin(line.text().codePointAt(0))
                    && KnowledgeTextPatterns.classify(line.text()) == LineClass.TEXT) {
                String joined = previous.text().substring(0, previous.text().length() - 1) + line.text();
                result.set(result.size() - 1, new Line(joined, line.endedWithSpace(), joined.length()));
                stats.hyphenJoins++;
            } else result.add(line);
        }
        return result;
    }

    private static boolean endsWithLowerHyphen(String text) {
        int n = text.length();
        return n >= 2 && text.charAt(n - 1) == '-' && isLowerLatin(text.charAt(n - 2));
    }

    private static boolean isLowerLatin(int cp) {
        return Character.isLowerCase(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN;
    }

    /** 10.4: joins the visual lines of one paragraph again; the length check uses the physical line, not the result. */
    private static List<Line> unwrap(List<Line> lines, Stats stats) {
        var textLengths = lines.stream().filter(l -> !l.text().isEmpty())
                .filter(l -> KnowledgeTextPatterns.classify(l.text()) == LineClass.TEXT)
                .map(l -> l.text().length()).sorted().toList();
        if (textLengths.size() < UNWRAP_MIN_TEXT_LINES) return lines;
        int reference = textLengths.get((int) Math.ceil(REFERENCE_PERCENTILE * textLengths.size()) - 1);
        if (reference < UNWRAP_MIN_REFERENCE) return lines;
        var result = new ArrayList<Line>();
        Line lastPhysical = null;
        for (Line line : lines) {
            Line previous = result.isEmpty() ? null : result.get(result.size() - 1);
            if (previous != null && lastPhysical != null && !previous.text().isEmpty() && !line.text().isEmpty()
                    && joinable(lastPhysical, line, reference)) {
                String a = previous.text();
                String separator = separator(a, lastPhysical.endedWithSpace(), line.text());
                result.set(result.size() - 1, new Line(a + separator + line.text(), line.endedWithSpace(), line.physicalLength()));
                stats.joinedLines++;
            } else result.add(line);
            lastPhysical = line;
        }
        return result;
    }

    private static boolean joinable(Line a, Line b, int reference) {
        LineClass ca = KnowledgeTextPatterns.classify(a.text());
        if (ca == LineClass.MARKER_HEADING || ca == LineClass.KEYWORD_HEADING || ca == LineClass.NUMBERED_HEADING
                || ca == LineClass.TABLE_ROW) return false;
        if (KnowledgeTextPatterns.classify(b.text()) != LineClass.TEXT) return false;
        return a.physicalLength() >= UNWRAP_FULL_LINE_SHARE * reference;
    }

    private static String separator(String a, boolean aEndedWithSpace, String b) {
        int n = a.length();
        if (n >= 2 && a.charAt(n - 1) == '-' && Character.isLetterOrDigit(a.charAt(n - 2))) return "";
        if (!aEndedWithSpace && noSpaceScript(a.codePointBefore(n)) && noSpaceScript(b.codePointAt(0))) return "";
        return " ";
    }

    private static boolean noSpaceScript(int cp) {
        if (!Character.isLetter(cp) && Character.getType(cp) != Character.NON_SPACING_MARK
                && Character.getType(cp) != Character.COMBINING_SPACING_MARK) return false;
        return switch (Character.UnicodeScript.of(cp)) {
            case THAI, LAO, KHMER, MYANMAR, HAN, HIRAGANA, KATAKANA -> true;
            default -> false;
        };
    }

    /** Section 9 step 4 and the format of section 12: at most one blank line in a row, none at the start or the end. */
    private static String blankLines(List<Line> lines) {
        var out = new StringBuilder();
        boolean pendingBlank = false;
        for (Line line : lines) {
            if (line.text().isEmpty()) {
                pendingBlank = !out.isEmpty();
                continue;
            }
            if (!out.isEmpty()) out.append(pendingBlank ? "\n\n" : "\n");
            out.append(line.text());
            pendingBlank = false;
        }
        return out.toString();
    }

    public static boolean hasLetterOrDigit(String text) {
        return text.codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
