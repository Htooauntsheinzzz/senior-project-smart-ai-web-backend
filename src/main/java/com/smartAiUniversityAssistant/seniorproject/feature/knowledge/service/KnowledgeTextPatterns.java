package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import java.util.regex.Pattern;

/**
 * Classification of one trimmed, normalized line (spec file 48, section 8), used by cleaning and chunking. A CLAUSE
 * ("Article 5.", "ข้อ 13") starts a block but is not a heading: it is the rule itself, not a title above it.
 */
public final class KnowledgeTextPatterns {
    private KnowledgeTextPatterns() {}

    public enum LineClass { MARKER_HEADING, KEYWORD_HEADING, NUMBERED_HEADING, CLAUSE, LIST_ITEM, TABLE_ROW, TEXT }

    private static final String DIGITS = "[0-9๐-๙]+";
    /** Number or (upper-case) Roman numeral, followed by the end of the line, a space or punctuation. */
    private static final String NUMBER = "(?:" + DIGITS + "|[IVXLCDM]+)(?=$|[\\s.:,;)\\-–—])";
    private static final Pattern MARKER = Pattern.compile("^#{1,6} \\S.*");
    private static final Pattern KEYWORD = Pattern.compile(
            "^(?:(?i:chapter|section|part)\\s+" + NUMBER
            + "|(?i:appendix|annex)\\s+(?:" + NUMBER + "|[A-Za-z](?=$|[\\s.:,;)\\-–—]))"
            + "|(?:หมวดที่|หมวด|ส่วนที่|บทที่|ตอนที่)\\s*" + DIGITS + "(?=$|[\\s.:,;)\\-–—])"
            + "|ภาคผนวก\\s*(?:" + DIGITS + "|[ก-ฮ])(?=$|[\\s.:,;)\\-–—])).*");
    private static final Pattern NUMBERED = Pattern.compile("^(?:0|[1-9][0-9]?)(?:\\.(?:0|[1-9][0-9]?)){1,4}\\.? \\S.*");
    private static final Pattern CLAUSE = Pattern.compile(
            "^(?:(?i:article|clause)\\s+" + DIGITS + "(?=$|[\\s.:,;)])|(?:ข้อ|มาตรา)\\s*" + DIGITS + "(?=$|[\\s.:,;)])).*");
    private static final Pattern LIST = Pattern.compile(
            "^(?:[-•*–▪●○◦]"
            + "|\\((?:[0-9]{1,3}|[a-z]|[ivx]{1,5}|[ก-ฮ])\\)"
            + "|(?:[0-9]{1,3}|[a-z]|[ivx]{1,5}|[ก-ฮ])[.)]) \\S.*");

    public static LineClass classify(String line) {
        if (line == null || line.isEmpty()) return LineClass.TEXT;
        if (MARKER.matcher(line).matches()) return LineClass.MARKER_HEADING;
        if (line.length() <= 150 && !endsWith(line, ".,;") && KEYWORD.matcher(line).matches()) return LineClass.KEYWORD_HEADING;
        if (line.length() <= 100 && !endsWith(line, ".,;:") && NUMBERED.matcher(line).matches()) return LineClass.NUMBERED_HEADING;
        if (CLAUSE.matcher(line).matches()) return LineClass.CLAUSE;
        if (LIST.matcher(line).matches()) return LineClass.LIST_ITEM;
        if (line.contains(" | ")) return LineClass.TABLE_ROW;
        return LineClass.TEXT;
    }

    /** Anything except plain TEXT starts a new block. */
    public static boolean startsBlock(String line) { return classify(line) != LineClass.TEXT; }

    public static boolean isHeading(String line) {
        LineClass c = classify(line);
        return c == LineClass.MARKER_HEADING || c == LineClass.KEYWORD_HEADING || c == LineClass.NUMBERED_HEADING;
    }

    /** Number of # of a marker heading, 0 for any other line. */
    public static int markerLevel(String line) {
        if (!MARKER.matcher(line).matches()) return 0;
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') level++;
        return level;
    }

    private static boolean endsWith(String line, String characters) {
        return characters.indexOf(line.charAt(line.length() - 1)) >= 0;
    }
}
