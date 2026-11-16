package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeChunkingProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeTextPatterns.LineClass;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.BreakIterator;
import java.util.*;
import java.util.function.Consumer;

/**
 * Splits cleaned text into chunks (spec file 49). Character-based and structure-aware: headings start new chunks when the
 * current one has enough body text, long lines are cut at sentence, space, word and finally grapheme boundaries (never
 * inside a grapheme cluster or a surrogate pair), a SIZE close starts the next chunk with an overlap or, inside a table,
 * with the table's header row. Streaming: only the current chunk and one emitted chunk (for merging) are kept. Pure
 * function: no database, no network; the same input and configuration always give the same chunks.
 */
public final class KnowledgeTextChunker {
    public static final int TITLE_MAX = 500;
    private static final Locale THAI = Locale.forLanguageTag("th");

    private enum Kind { HEADING, BODY, OVERLAP }
    private enum Reason { HEADING, SHEET, SIZE, END }

    private record Piece(String text, int page, Kind kind, boolean tableRow) {}

    private final int target, max, min, overlap;
    private final Consumer<ChunkDraft> sink;

    private final List<Piece> current = new ArrayList<>();
    private int length, body;
    private String chapterTitle, subTitle;
    private boolean inSheet;
    private String sheetLabel;
    private Piece tableHeader;
    private boolean lastLineWasTableRow;
    private Pending pending;
    private long splitLines;

    /** The emitted chunk that a later heading-only or small end chunk can still be appended to. */
    private record Pending(List<Piece> pieces, String sectionTitle, boolean sheet, String sheetLabel) {}

    public KnowledgeTextChunker(KnowledgeChunkingProperties properties, Consumer<ChunkDraft> sink) {
        if (!properties.isConsistent()) throw new IllegalArgumentException("Inconsistent chunking configuration");
        this.target = properties.targetChars();
        this.max = properties.maxChars();
        this.min = properties.minChars();
        this.overlap = properties.overlapChars();
        this.sink = sink;
    }

    /** Lines of the input that were longer than target-chars and had to be split. */
    public long splitLines() { return splitLines; }

    /** Feeds one unit (a row of academic_document_pages) in page order; blank lines are ignored. */
    public void unit(int pageNumber, DocumentUnitType type, String label, String cleanedText) {
        boolean sheet = type == DocumentUnitType.SHEET;
        String normalizedLabel = sheet && label != null ? KnowledgeTextNormalizer.normalize(label).strip() : null;
        if (sheet && (!inSheet || !Objects.equals(sheetLabel, normalizedLabel)) || !sheet && inSheet) {
            close(Reason.SHEET, null);
            inSheet = sheet;
            sheetLabel = normalizedLabel;
            tableHeader = null;
            lastLineWasTableRow = false;
        }
        for (String line : cleanedText.split("\n")) {
            if (line.isBlank()) continue;
            line(pageNumber, line.strip());
        }
    }

    private void line(int page, String line) {
        LineClass type = KnowledgeTextPatterns.classify(line);
        boolean heading = (type == LineClass.MARKER_HEADING || type == LineClass.KEYWORD_HEADING
                || type == LineClass.NUMBERED_HEADING);
        if (heading) {
            String text = type == LineClass.MARKER_HEADING ? line.substring(KnowledgeTextPatterns.markerLevel(line) + 1).strip() : line;
            if (!text.isEmpty() && text.length() <= target) {
                lastLineWasTableRow = false;
                heading(new Piece(text, page, Kind.HEADING, false), type == LineClass.NUMBERED_HEADING
                        || type == LineClass.MARKER_HEADING && KnowledgeTextPatterns.markerLevel(line) > 1);
                return;
            }
            if (!text.isEmpty()) line = text;
        }
        boolean tableRow = type == LineClass.TABLE_ROW;
        if (tableRow && (!lastLineWasTableRow || inSheet && tableHeader == null)) {
            if (!inSheet || tableHeader == null) tableHeader = new Piece(line, page, Kind.OVERLAP, true);
        }
        lastLineWasTableRow = tableRow;
        if (line.length() <= target) {
            body(new Piece(line, page, Kind.BODY, tableRow));
            return;
        }
        splitLines++;
        for (String part : split(line, target)) body(new Piece(part, page, Kind.BODY, false));
    }

    private void heading(Piece heading, boolean sub) {
        if (body >= min) close(Reason.HEADING, heading);
        if (sub) subTitle = heading.text();
        else {
            chapterTitle = heading.text();
            subTitle = null;
        }
        append(heading);
    }

    private void body(Piece piece) {
        if (length + 1 + piece.text().length() > target && body >= min) close(Reason.SIZE, piece);
        if (length + 1 + piece.text().length() > max) close(Reason.SIZE, piece);
        append(piece);
    }

    private void append(Piece piece) {
        length += (current.isEmpty() ? 0 : 1) + piece.text().length();
        if (piece.kind() == Kind.BODY) body += piece.text().length();
        current.add(piece);
    }

    /** Ends the input: closes the current chunk and emits the one kept for merging. */
    public void finish() {
        close(Reason.END, null);
        flush();
    }

    private void close(Reason reason, Piece next) {
        List<Piece> closed = List.copyOf(current);
        int closedBody = body;
        current.clear();
        length = 0;
        body = 0;
        if (closed.isEmpty()) return;
        if (reason == Reason.SIZE && next != null) startAfterSize(closed, next);
        if (closed.stream().noneMatch(p -> KnowledgeTextChunker.hasLetterOrDigit(p.text()))) return;
        boolean headingsOnly = closed.stream().allMatch(p -> p.kind() == Kind.HEADING);
        boolean hasHeading = closed.stream().anyMatch(p -> p.kind() == Kind.HEADING);
        if (headingsOnly && pending != null && joinedLength(pending.pieces(), closed) <= max) {
            merge(closed);
            return;
        }
        if (!headingsOnly && closedBody < min && (reason == Reason.END || reason == Reason.SHEET) && !hasHeading && pending != null
                && pending.sheet() == inSheet && Objects.equals(pending.sheetLabel(), sheetLabel)
                && joinedLength(pending.pieces(), closed) <= max) {
            merge(closed);
            return;
        }
        flush();
        pending = new Pending(closed, sectionTitle(), inSheet, sheetLabel);
    }

    private void merge(List<Piece> closed) {
        var pieces = new ArrayList<>(pending.pieces());
        pieces.addAll(closed);
        pending = new Pending(pieces, pending.sectionTitle(), pending.sheet(), pending.sheetLabel());
    }

    private static int joinedLength(List<Piece> a, List<Piece> b) {
        return text(a).length() + 1 + text(b).length();
    }

    private void flush() {
        if (pending == null) return;
        sink.accept(draft(pending.pieces(), pending.sectionTitle()));
        pending = null;
    }

    /** After a SIZE close: the table header row inside a table, otherwise the overlap unless the next piece is a heading. */
    private void startAfterSize(List<Piece> closed, Piece next) {
        Piece last = closed.get(closed.size() - 1);
        if (last.tableRow() && next.tableRow()) {
            if (tableHeader != null && !tableHeader.text().equals(next.text())
                    && tableHeader.text().length() + 1 + next.text().length() <= max) append(tableHeader);
            return;
        }
        if (next.kind() == Kind.HEADING || overlap == 0) return;
        List<Piece> copy = overlapOf(closed);
        int copyLength = copy.isEmpty() ? 0 : text(copy).length();
        if (copy.isEmpty() || copyLength + 1 + next.text().length() > max) return;
        copy.forEach(this::append);
    }

    /** Section 10: the end of the previous chunk, starting at a line, sentence, space or word boundary inside the window. */
    private List<Piece> overlapOf(List<Piece> closed) {
        String content = text(closed);
        int[] starts = new int[closed.size()];
        for (int i = 0, offset = 0; i < closed.size(); i++) {
            starts[i] = offset;
            offset += closed.get(i).text().length() + 1;
        }
        int windowStart = Math.max(0, content.length() - overlap);
        int start = -1;
        for (int i = 0; i < closed.size() && start < 0; i++) if (starts[i] >= windowStart) start = starts[i];
        if (start < 0) start = firstBoundary(BreakIterator.getSentenceInstance(Locale.ROOT), content, windowStart);
        if (start < 0) {
            int space = content.indexOf(' ', windowStart);
            if (space >= 0 && space + 1 < content.length()) start = space + 1;
        }
        if (start < 0) start = firstBoundary(BreakIterator.getWordInstance(THAI), content, windowStart);
        if (start < 0) return List.of();
        start = nextGraphemeBoundary(content, start);
        var result = new ArrayList<Piece>();
        for (int i = 0; i < closed.size(); i++) {
            int s = starts[i], e = s + closed.get(i).text().length();
            if (e <= start) continue;
            Piece piece = closed.get(i);
            if (piece.kind() == Kind.HEADING) continue; // heading lines are never copied
            String part = (start > s ? content.substring(start, e) : piece.text()).strip();
            if (!part.isEmpty()) result.add(new Piece(part, piece.page(), Kind.OVERLAP, piece.tableRow() && start <= s));
        }
        return result;
    }

    private static int firstBoundary(BreakIterator iterator, String text, int from) {
        iterator.setText(text);
        int b = iterator.following(Math.max(0, from - 1));
        while (b != BreakIterator.DONE && b < text.length()) {
            if (b >= from && !Character.isWhitespace(text.charAt(b))) return b;
            b = iterator.next();
        }
        return -1;
    }

    private static int nextGraphemeBoundary(String text, int position) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(text);
        if (graphemes.isBoundary(position)) return position;
        int next = graphemes.following(position);
        return next == BreakIterator.DONE ? text.length() : next;
    }

    /** Section 8: cuts a line longer than target into pieces at sentence, space, word, then grapheme boundaries. */
    public static List<String> split(String line, int target) {
        var pieces = new ArrayList<String>();
        String rest = line.strip();
        while (rest.length() > target) {
            int cut = cut(rest, target);
            String piece = rest.substring(0, cut).strip();
            if (!piece.isEmpty()) pieces.add(piece);
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) pieces.add(rest);
        return pieces;
    }

    private static int cut(String text, int target) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(text);
        int cut = lastBoundary(BreakIterator.getSentenceInstance(Locale.ROOT), text, target);
        if (cut <= 0) {
            int space = text.lastIndexOf(' ', target);
            if (space > 0) cut = space + 1;
        }
        if (cut <= 0) cut = lastBoundary(BreakIterator.getWordInstance(THAI), text, target);
        if (cut > 0 && !graphemes.isBoundary(cut)) cut = graphemes.preceding(cut);
        if (cut <= 0) {
            cut = graphemes.preceding(target + 1);
            if (cut <= 0) cut = graphemes.following(0); // a single cluster longer than target cannot be cut
        }
        return cut;
    }

    /** The last boundary in (0, target] whose piece is not only spaces. */
    private static int lastBoundary(BreakIterator iterator, String text, int target) {
        iterator.setText(text);
        int b = iterator.preceding(Math.min(target + 1, text.length()));
        while (b != BreakIterator.DONE && b > 0) {
            if (!text.substring(0, b).isBlank()) return b;
            b = iterator.previous();
        }
        return -1;
    }

    private String sectionTitle() {
        String title;
        if (inSheet) title = sheetLabel;
        else if (chapterTitle != null && subTitle != null) title = chapterTitle + " › " + subTitle;
        else title = chapterTitle != null ? chapterTitle : subTitle;
        if (title == null || title.isEmpty()) return null;
        return truncate(title, TITLE_MAX);
    }

    /** Cuts to at most max chars, never inside a grapheme cluster. */
    public static String truncate(String text, int max) {
        if (text.length() <= max) return text;
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(text);
        int cut = graphemes.isBoundary(max) ? max : graphemes.preceding(max);
        return text.substring(0, Math.max(cut, 0));
    }

    private static String text(List<Piece> pieces) {
        var joiner = new StringJoiner("\n");
        for (Piece p : pieces) joiner.add(p.text());
        return joiner.toString();
    }

    private static ChunkDraft draft(List<Piece> pieces, String sectionTitle) {
        String content = text(pieces);
        int pageStart = pieces.stream().mapToInt(Piece::page).min().orElse(1);
        int pageEnd = pieces.stream().mapToInt(Piece::page).max().orElse(1);
        String input = embeddingInput(sectionTitle, content);
        return new ChunkDraft(content, sectionTitle, pageStart, pageEnd, content.length(), tokenEstimate(input), sha256(input));
    }

    /** The exact text sent to the embedding model (spec file 49, section 11); also the input of content_hash. */
    public static String embeddingInput(String sectionTitle, String content) {
        return sectionTitle == null ? content : sectionTitle + "\n\n" + content;
    }

    /** max(1, ceil(asciiChars / 4 + otherChars / 1.5)). An estimate for limits and statistics only. */
    public static int tokenEstimate(String text) {
        long ascii = text.chars().filter(c -> c < 128).count();
        long other = text.length() - ascii;
        return (int) Math.max(1, Math.ceil(ascii / 4.0 + other / 1.5));
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean hasLetterOrDigit(String text) { return text.codePoints().anyMatch(Character::isLetterOrDigit); }
}
