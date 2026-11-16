package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.chunking;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeChunkingProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.BreakIterator;
import java.util.*;
import org.junit.jupiter.api.Test;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType.*;
import static org.assertj.core.api.Assertions.*;

/** Spec file 49, section 18.1. No Spring context. */
class KnowledgeTextChunkerTests {
    static final KnowledgeChunkingProperties DEFAULTS = new KnowledgeChunkingProperties(1000, 1500, 200, 150, 5000);
    static final String THAI_SENTENCE = "นักศึกษาต้องลงทะเบียนเรียนรายวิชาตามแผนการศึกษาของหลักสูตรในแต่ละภาคการศึกษาปกติ";
    static final String MYANMAR_WORD = "ကျောင်းသားများသည်";

    record Unit(int page, DocumentUnitType type, String label, String text) {}

    static List<ChunkDraft> chunk(KnowledgeChunkingProperties properties, Unit... units) {
        var chunks = new ArrayList<ChunkDraft>();
        var chunker = new KnowledgeTextChunker(properties, chunks::add);
        for (Unit u : units) chunker.unit(u.page(), u.type(), u.label(), u.text());
        chunker.finish();
        return chunks;
    }

    static List<ChunkDraft> chunk(String text) { return chunk(DEFAULTS, new Unit(1, TEXT, null, text)); }

    static String sentences(String prefix, int count) {
        var b = new StringBuilder();
        for (int i = 0; i < count; i++) b.append(i == 0 ? "" : " ").append(prefix).append(' ').append(i).append(" says the rule applies to every student of the university.");
        return b.toString();
    }

    @Test
    void workedExampleOfSection15() throws Exception {
        String raw = ExtractTextStage.sanitize(Files.readString(Path.of("docker/dev-fixtures/rag-sample-regulations.txt")).stripTrailing());
        String cleaned = new KnowledgeTextCleaner(true, true, true).clean(TEXT, 1, raw, KnowledgeTextCleaner.RepeatedLines.none()).text();
        var chunks = chunk(cleaned);
        assertThat(chunks).extracting(ChunkDraft::sectionTitle).containsExactly("Chapter 1 General Provisions",
                "Chapter 2 Registration", "Chapter 3 Tuition and Fees", "Chapter 4 Examinations", "Chapter 5 Graduation",
                "หมวด 6 การลาพักการศึกษา");
        assertThat(chunks).extracting(ChunkDraft::charCount).containsExactly(425, 631, 306, 315, 273, 208);
        assertThat(chunks.get(0).content()).startsWith("SAMPLE DATA FOR DEVELOPMENT ONLY");
        for (int i = 1; i < 6; i++) assertThat(chunks.get(i).content()).startsWith(chunks.get(i).sectionTitle());
        assertThat(chunks.get(4).content()).isEqualTo("Chapter 5 Graduation\nArticle 11. To graduate, a student must complete at least "
                + "120 credits required by the curriculum and have a cumulative grade point average of at least 2.00.\nArticle 12. A "
                + "student must apply for graduation within the first 30 days of the final semester.");
        assertThat(chunks).allSatisfy(c -> assertThat(c.pageStart()).isEqualTo(1));
    }

    @Test
    void noChunkExceedsMaxForAnyScript() {
        String english = sentences("Article", 400);
        String thai = (THAI_SENTENCE + " ").repeat(80);
        String myanmar = (MYANMAR_WORD + " ").repeat(300);
        String mixed = english.substring(0, 3000) + " " + thai.substring(0, 3000);
        String noBreaks = "x".repeat(20_000);
        for (String text : List.of(english, thai, myanmar, mixed, noBreaks)) {
            var chunks = chunk(text);
            assertThat(chunks).isNotEmpty().allSatisfy(c -> assertThat(c.charCount()).isLessThanOrEqualTo(1500));
        }
    }

    @Test
    void sizeClosedChunksHaveEnoughBodyAndInconsistentConfigurationIsRejected() {
        var chunks = chunk(sentences("Rule", 200));
        assertThat(chunks.size()).isGreaterThan(5);
        // every chunk but the last was closed for SIZE: more than min-chars of new text after its overlap
        for (ChunkDraft c : chunks.subList(0, chunks.size() - 1)) assertThat(c.charCount()).isGreaterThanOrEqualTo(200 + 1);
        assertThatThrownBy(() -> new KnowledgeTextChunker(new KnowledgeChunkingProperties(1000, 1300, 200, 150, 10), c -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new KnowledgeChunkingProperties(1000, 1300, 200, 150, 10).isConsistent()).isFalse();
        assertThat(DEFAULTS.isConsistent()).isTrue();
    }

    @Test
    void longParagraphsSplitAtTheRightBoundaries() {
        String english = sentences("Clause", 40);
        var pieces = KnowledgeTextChunker.split(english, 1000);
        assertThat(pieces).allSatisfy(p -> {
            assertThat(p.length()).isLessThanOrEqualTo(1000);
            assertThat(p).endsWith(".");
        });
        String thai = (THAI_SENTENCE + " ").repeat(30).strip();
        assertThat(KnowledgeTextChunker.split(thai, 1000)).allSatisfy(p -> assertThat(p).endsWith("ปกติ"));
        String run = THAI_SENTENCE.repeat(40);  // about 3,000 characters without a space
        var words = KnowledgeTextChunker.split(run, 1000);
        assertThat(words).hasSizeGreaterThan(2).allSatisfy(p -> assertThat(p.length()).isLessThanOrEqualTo(1000));
        assertThat(String.join("", words)).isEqualTo(run);
        assertGraphemeCuts(run, words);
        String clusters = "กิ้".repeat(700);  // KO KAI + SARA I + MAI THO: no word boundary inside the run
        var graphemes = KnowledgeTextChunker.split(clusters, 1000);
        assertThat(String.join("", graphemes)).isEqualTo(clusters);
        assertGraphemeCuts(clusters, graphemes);
        String emoji = "😀".repeat(600);
        var pairs = KnowledgeTextChunker.split(emoji, 1000);
        assertThat(String.join("", pairs)).isEqualTo(emoji);
        assertThat(pairs).allSatisfy(p -> assertThat(Character.isLowSurrogate(p.charAt(0))).isFalse());
    }

    /** Length of the longest whole-line prefix of next that the previous chunk ends with (0 when none). */
    static int overlapLength(String previous, String next) {
        int best = 0;
        for (int k = next.indexOf('\n'); k > 0; k = next.indexOf('\n', k + 1))
            if (previous.endsWith(next.substring(0, k))) best = k;
        return best;
    }

    private static void assertGraphemeCuts(String original, List<String> pieces) {
        BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
        graphemes.setText(original);
        int offset = 0;
        for (String piece : pieces) {
            offset = original.indexOf(piece, offset) + piece.length();
            assertThat(graphemes.isBoundary(offset)).as("cut at " + offset).isTrue();
        }
    }

    @Test
    void chunkBordersInThaiAndMyanmarAreGraphemeBoundaries() {
        for (String text : List.of(THAI_SENTENCE.repeat(80), (MYANMAR_WORD).repeat(200))) {
            var chunks = chunk(text);
            assertThat(chunks.size()).isGreaterThan(1);
            for (ChunkDraft c : chunks) {
                for (String line : c.content().split("\n")) {
                    int at = text.indexOf(line);
                    assertThat(at).isGreaterThanOrEqualTo(0);
                    BreakIterator g = BreakIterator.getCharacterInstance(Locale.ROOT);
                    g.setText(text);
                    assertThat(g.isBoundary(at)).isTrue();
                    assertThat(g.isBoundary(at + line.length())).isTrue();
                }
            }
        }
    }

    @Test
    void linesAreNeverMerged() {
        var chunks = chunk("First line of text here.\nSecond line here.\nThird line.");
        assertThat(chunks).singleElement().satisfies(c -> assertThat(c.content()).isEqualTo("First line of text here.\nSecond line here.\nThird line."));
    }

    @Test
    void headingsTitlesAndClauses() {
        String body = "x".repeat(250);
        var chunks = chunk("# Chapter 2 Registration\n" + body + "\n## 2.1 Add and Drop\n" + body + "\nArticle 7. Clause text.\n"
                + "Chapter 3 Fees\n" + body);
        assertThat(chunks).extracting(ChunkDraft::sectionTitle).containsExactly("Chapter 2 Registration",
                "Chapter 2 Registration › 2.1 Add and Drop", "Chapter 3 Fees");
        assertThat(chunks.get(0).content()).isEqualTo("Chapter 2 Registration\n" + body);
        assertThat(chunks.get(1).content()).startsWith("2.1 Add and Drop\n").contains("Article 7. Clause text.");
        assertThat(chunks).allSatisfy(c -> assertThat(c.content()).doesNotContain("#"));
        // too little body: the heading does not close the chunk
        var small = chunk("Intro words.\nChapter 1 Start\n" + body);
        assertThat(small).singleElement().satisfies(c -> assertThat(c.sectionTitle()).isEqualTo("Chapter 1 Start"));
        assertThat(chunk("Plain text without headings.")).singleElement().satisfies(c -> assertThat(c.sectionTitle()).isNull());
        var longTitle = chunk(DEFAULTS, new Unit(1, SHEET, "S".repeat(600), "a | b"));
        assertThat(longTitle.get(0).sectionTitle()).hasSize(500);
    }

    @Test
    void overlapAfterSizeClose() {
        var chunks = chunk(sentences("Rule", 60));
        assertThat(chunks.size()).isGreaterThan(2);
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).content();
            String first = chunks.get(i).content().split("\n")[0];
            assertThat(previous).endsWith(first);
            assertThat(first.length()).isLessThanOrEqualTo(150);
            assertThat(first).startsWith("Rule");  // a sentence start (the window has no line start)
        }
        var lines = new StringBuilder();
        for (int i = 0; i < 40; i++) lines.append("Line ").append(i).append(" of the regulation text goes here.\n");
        var lineChunks = chunk(lines.toString());
        assertThat(lineChunks.size()).isGreaterThan(1);
        for (int i = 1; i < lineChunks.size(); i++) {
            int k = overlapLength(lineChunks.get(i - 1).content(), lineChunks.get(i).content());
            assertThat(k).isBetween(1, 150);
            assertThat(lineChunks.get(i).content()).startsWith("Line ");  // starts at a line start
        }
        var noOverlap = chunk(new KnowledgeChunkingProperties(1000, 1500, 200, 0, 5000), new Unit(1, TEXT, null, sentences("Rule", 60)));
        for (int i = 1; i < noOverlap.size(); i++)
            assertThat(noOverlap.get(i - 1).content()).doesNotEndWith(noOverlap.get(i).content().split("\n")[0]);
        // a HEADING close carries no overlap: the next chunk starts with the heading
        var beforeHeading = chunk(sentences("Rule", 8) + "\nChapter 9 New\n" + "y".repeat(300));
        assertThat(beforeHeading).hasSize(2);
        assertThat(beforeHeading.get(1).content()).isEqualTo("Chapter 9 New\n" + "y".repeat(300));
    }

    @Test
    void tablesRepeatTheirHeaderRowAndSheetsNeverShareAChunk() {
        var rows = new StringBuilder("Code | Title | Credits\n");
        for (int i = 0; i < 80; i++) rows.append("CS").append(100 + i).append(" | Course number ").append(i).append(" title | 3\n");
        var chunks = chunk(rows.toString());
        assertThat(chunks.size()).isGreaterThan(1);
        for (ChunkDraft c : chunks) assertThat(c.content()).startsWith("Code | Title | Credits\n");
        var sheets = chunk(DEFAULTS, new Unit(1, SHEET, "Courses", rows.toString()), new Unit(2, SHEET, "Fees", "Item | Amount\nLate | 500"));
        assertThat(sheets).extracting(ChunkDraft::sectionTitle).contains("Courses", "Fees");
        assertThat(sheets.stream().filter(c -> c.sectionTitle().equals("Fees"))).singleElement()
                .satisfies(c -> assertThat(c.content()).isEqualTo("Item | Amount\nLate | 500"));
        assertThat(sheets.stream().filter(c -> c.sectionTitle().equals("Courses")))
                .allSatisfy(c -> assertThat(c.content()).startsWith("Code | Title | Credits").doesNotContain("Late | 500"));
    }

    @Test
    void pagesAndContinuationUnits() {
        var chunks = chunk(DEFAULTS, new Unit(3, PAGE, null, "Text on page three that continues"), new Unit(4, PAGE, null, "onto page four."));
        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.pageStart()).isEqualTo(3);
            assertThat(c.pageEnd()).isEqualTo(4);
        });
        var sections = chunk(DEFAULTS, new Unit(1, SECTION, "Chapter 1", "# Chapter 1\nPart one text."), new Unit(2, SECTION, "Chapter 1", "Part two text."));
        assertThat(sections).singleElement().satisfies(c -> assertThat(c.content()).isEqualTo("Chapter 1\nPart one text.\nPart two text."));
    }

    @Test
    void hashTokenEstimateAndDeterminism() throws Exception {
        var titled = chunk("Chapter 1 Rules\n" + "z".repeat(300)).get(0);
        assertThat(titled.contentHash()).isEqualTo(sha("Chapter 1 Rules\n\n" + titled.content()));
        var untitled = chunk("Plain words only.").get(0);
        assertThat(untitled.contentHash()).isEqualTo(sha("Plain words only.")).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(KnowledgeTextChunker.tokenEstimate("abcd")).isEqualTo(1);
        assertThat(KnowledgeTextChunker.tokenEstimate("ก".repeat(1500))).isEqualTo(1000);
        assertThat(KnowledgeTextChunker.embeddingInput(null, "c")).isEqualTo("c");
        assertThat(KnowledgeTextChunker.embeddingInput("T", "c")).isEqualTo("T\n\nc");
        String text = sentences("Rule", 80) + "\nChapter 2 X\n" + THAI_SENTENCE.repeat(30);
        assertThat(chunk(text)).isEqualTo(chunk(text));
    }

    private static String sha(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
