package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeAudience.*;
import static org.assertj.core.api.Assertions.*;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.DeterministicEmbeddingClient;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.QueryEmbeddingCache;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.KnowledgeSearchServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.KnowledgeSearchServiceImpl.Ranked;
import com.smartAiUniversityAssistant.seniorproject.security.CurrentStudentProvider;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Spec file 54, section 14.1: audience, reserve, neighbours and options with a fake vector store. No Spring context. */
class KnowledgeCurriculumSearchUnitTests {
    static final long P1 = 1, P2 = 2, D1 = 11;
    static final StudentKnowledgeScope SCOPE = new StudentKnowledgeScope(P1, D1);

    /** A match with score = 1 − distance; document links as given. */
    static KnowledgeChunkMatch match(long chunkId, long documentId, double score, Long programId, Long curriculumProgramId,
            Long departmentId, long fileId, int chunkIndex, String section) {
        return new KnowledgeChunkMatch(chunkId, chunkIndex, section, 1, 1, "content " + chunkId, fileId, 1, "txt", documentId,
                "Doc " + documentId, "2026-2027", 3L, "REG", "Regulations", curriculumProgramId == null ? null : 50L, programId,
                departmentId, 1 - score, curriculumProgramId);
    }

    static KnowledgeChunkMatch university(long chunkId, long documentId, double score) {
        return match(chunkId, documentId, score, null, null, null, 100 + documentId, (int) chunkId, "S" + chunkId);
    }

    static KnowledgeChunkMatch program(long chunkId, long documentId, double score) {
        return match(chunkId, documentId, score, P1, null, null, 100 + documentId, (int) chunkId, "S" + chunkId);
    }

    static final class FakeStore implements KnowledgeVectorStore {
        List<KnowledgeChunkMatch> general = List.of(), program = List.of(), around = List.of();
        int searches, programSearches, findCalls;
        List<KnowledgeChunkPosition> lastPositions;
        public List<KnowledgeChunkMatch> search(float[] v, String model, KnowledgeSearchFilter filter, int limit) {
            searches++;
            if (filter.programDocumentsOnly() != null) {
                programSearches++;
                return program;
            }
            return general;
        }
        public List<KnowledgeChunkMatch> findChunks(String model, List<KnowledgeChunkPosition> positions) {
            findCalls++;
            lastPositions = positions;
            return around.stream().filter(m -> positions.contains(new KnowledgeChunkPosition(m.fileId(), m.chunkIndex()))).toList();
        }
        public int reuseEmbeddings(long fileId, String model) { throw new UnsupportedOperationException(); }
        public void saveEmbeddings(Map<Long, float[]> v, String model) { throw new UnsupportedOperationException(); }
        public FileIndexCounts countByFile(long fileId, String model) { throw new UnsupportedOperationException(); }
        public int deleteByFile(long fileId, int maxRows) { throw new UnsupportedOperationException(); }
        public int removeOutdated(int maxRows) { throw new UnsupportedOperationException(); }
        public IndexStatistics statistics(String model) { throw new UnsupportedOperationException(); }
    }

    final FakeStore store = new FakeStore();
    final CurrentStudentProvider student = new CurrentStudentProvider() {
        @Override public long getCurrentStudentId() { return 42L; }
    };

    KnowledgeSearchServiceImpl service(double minScore, int maxPerDocument) {
        return new KnowledgeSearchServiceImpl(new DeterministicEmbeddingClient(), store, new QueryEmbeddingCache(null, Duration.ZERO),
                new KnowledgeSearchProperties(5, minScore, maxPerDocument, 100, Duration.ZERO), new KnowledgeCleaningProperties(true, true, true),
                student);
    }

    static List<Long> ids(List<KnowledgeSearchHit> hits) {
        return hits.stream().map(h -> Long.parseLong(h.content().substring(8))).toList();
    }

    @Test
    void audience() {
        assertThat(KnowledgeSearchServiceImpl.audience(match(1, 1, .9, P1, null, null, 1, 1, "x"), SCOPE)).isEqualTo(PROGRAM);
        assertThat(KnowledgeSearchServiceImpl.audience(match(1, 1, .9, null, P1, null, 1, 1, "x"), SCOPE)).isEqualTo(PROGRAM);
        assertThat(KnowledgeSearchServiceImpl.audience(match(1, 1, .9, null, null, D1, 1, 1, "x"), SCOPE)).isEqualTo(DEPARTMENT);
        assertThat(KnowledgeSearchServiceImpl.audience(match(1, 1, .9, P2, null, D1, 1, 1, "x"), SCOPE)).isEqualTo(DEPARTMENT);
        assertThat(KnowledgeSearchServiceImpl.audience(university(1, 1, .9), SCOPE)).isEqualTo(UNIVERSITY);
        var noProgram = new StudentKnowledgeScope(null, D1);
        assertThat(KnowledgeSearchServiceImpl.audience(match(1, 1, .9, P1, null, null, 1, 1, "x"), noProgram)).isEqualTo(UNIVERSITY);
        assertThat(KnowledgeSearchServiceImpl.audience(match(1, 1, .9, null, null, D1, 1, 1, "x"), noProgram)).isEqualTo(DEPARTMENT);
        assertThat(KnowledgeSearchServiceImpl.audience(university(1, 1, .9), null)).isNull();
    }

    // Section 7.3: a1 b1 a2 b2 a3 university-wide, c1 c2 c3 of the program.
    void exampleOf7_3(double c1, double c2) {
        store.general = List.of(university(1, 1, .81), university(2, 2, .78), university(3, 1, .74), university(4, 2, .70), university(5, 1, .66));
        store.program = List.of(program(6, 3, c1), program(7, 3, c2), program(8, 3, .41));
    }

    @Test
    void reserveTableOf7_3() {
        exampleOf7_3(.63, .58);
        var s = service(.50, 3);
        assertThat(ids(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 0)))).containsExactly(1L, 2L, 3L, 4L);
        assertThat(store.programSearches).isZero();
        var hits = s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 2, 0));
        assertThat(ids(hits)).containsExactly(1L, 2L, 6L, 7L);
        assertThat(hits).extracting(KnowledgeSearchHit::rank).containsExactly(1, 2, 3, 4);
        assertThat(hits).extracting(KnowledgeSearchHit::audience).containsExactly(UNIVERSITY, UNIVERSITY, PROGRAM, PROGRAM);
        assertThat(store.programSearches).isEqualTo(1);
        exampleOf7_3(.45, .42);
        assertThat(ids(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 2, 0)))).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void reserveDetails() {
        var scope = SCOPE;
        // In both lists: used once.
        var shared = program(6, 3, .9);
        var once = KnowledgeSearchServiceImpl.selectWithReserve(List.of(shared, university(1, 1, .8)), List.of(shared), 4, .5, 3, 2, scope);
        assertThat(once).extracting(r -> r.match().chunkId()).containsExactly(6L, 1L);
        // Three program candidates of one document, max-per-document 2, quota 3: two reserved.
        var three = List.of(program(6, 3, .6), program(7, 3, .59), program(8, 3, .58));
        var general = List.of(university(1, 1, .9), university(2, 2, .89), university(3, 4, .88));
        assertThat(KnowledgeSearchServiceImpl.selectWithReserve(general, three, 4, .5, 2, 3, scope))
                .extracting(r -> r.match().chunkId()).containsExactly(1L, 2L, 6L, 7L);
        // topK 2 and quota 3: two program hits.
        assertThat(KnowledgeSearchServiceImpl.selectWithReserve(general, three, 2, .5, 3, 3, scope))
                .extracting(r -> r.match().chunkId()).containsExactly(6L, 7L);
        // Ranked by distance even when a reserved hit is farther than a filled one.
        var ranked = KnowledgeSearchServiceImpl.selectWithReserve(general, three, 3, .5, 3, 1, scope);
        assertThat(ranked).extracting(r -> r.match().chunkId()).containsExactly(1L, 2L, 6L);
        assertThat(ranked).extracting(Ranked::rank).containsExactly(1, 2, 3);
    }

    @Test
    void noReserveWithoutQuotaOrProgram() {
        exampleOf7_3(.63, .58);
        var s = service(.50, 3);
        var plain = s.studentSearch("q", new StudentKnowledgeScope(null, D1), new StudentSearchOptions(4, 2, 0));
        assertThat(store.programSearches).isZero();
        assertThat(ids(plain)).containsExactly(1L, 2L, 3L, 4L);
        var expected = KnowledgeSearchServiceImpl.select(store.general, 4, .5, 3);
        assertThat(plain).extracting(KnowledgeSearchHit::score).containsExactlyElementsOf(expected.stream().map(Ranked::score).toList());
    }

    // Section 8.3: hits A#4 ("S4", rank 1) and B#2 ("X", rank 2); A#3 "S4", A#5 "S5", B#1 "X", B#3 "X" around them.
    void exampleOf8_3() {
        store.general = List.of(match(4, 1, .9, null, null, null, 101, 4, "S4"), match(12, 2, .8, null, null, null, 102, 2, "X"));
        store.around = List.of(match(3, 1, 0, null, null, null, 101, 3, "S4"), match(5, 1, 0, null, null, null, 101, 5, "S5"),
                match(11, 2, 0, null, null, null, 102, 1, "X"), match(13, 2, 0, null, null, null, 102, 3, "X"));
    }

    static List<String> layout(List<KnowledgeSearchHit> hits) {
        return hits.stream().map(h -> (h.title().equals("Doc 1") ? "A#" : "B#") + h.content().substring(8) + (h.neighbor() ? "n" : "")).toList();
    }

    @Test
    void neighbourTableOf8_3() {
        exampleOf8_3();
        var s = service(.5, 3);
        assertThat(layout(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 0)))).containsExactly("A#4", "B#12");
        assertThat(store.findCalls).isZero();
        assertThat(layout(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 1)))).containsExactly("A#3n", "A#4", "B#12");
        assertThat(layout(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 2)))).containsExactly("A#3n", "A#4", "B#11n", "B#12");
        var three = s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 3));
        assertThat(layout(three)).containsExactly("A#3n", "A#4", "B#11n", "B#12", "B#13n");
        assertThat(store.findCalls).isEqualTo(3);
        var neighbour = three.get(0);
        assertThat(neighbour.neighbor()).isTrue();
        assertThat(neighbour.rank()).isEqualTo(1);
        assertThat(neighbour.score()).isEqualTo(three.get(1).score());
        assertThat(neighbour.audience()).isEqualTo(UNIVERSITY);
    }

    @Test
    void neighbourDetails() {
        // A hit without a section title gets none.
        store.general = List.of(match(4, 1, .9, null, null, null, 101, 4, null));
        store.around = List.of(match(3, 1, 0, null, null, null, 101, 3, null));
        var s = service(.5, 3);
        assertThat(layout(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 2)))).containsExactly("A#4");
        // Neighbour that is also a hit is not added twice; a neighbour of two hits once.
        store.general = List.of(match(4, 1, .9, null, null, null, 101, 4, "S"), match(6, 1, .8, null, null, null, 101, 6, "S"),
                match(5, 1, .7, null, null, null, 101, 5, "S"));
        store.around = List.of(match(3, 1, 0, null, null, null, 101, 3, "S"), match(5, 1, 0, null, null, null, 101, 5, "S"),
                match(7, 1, 0, null, null, null, 101, 7, "S"));
        var hits = s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 4));
        assertThat(layout(hits)).containsExactly("A#3n", "A#4", "A#6", "A#7n", "A#5");
        // A position that is not eligible is skipped without using the budget.
        store.general = List.of(match(4, 1, .9, null, null, null, 101, 4, "S"), match(12, 2, .8, null, null, null, 102, 2, "X"));
        store.around = List.of(match(11, 2, 0, null, null, null, 102, 1, "X"));
        assertThat(layout(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 1)))).containsExactly("A#4", "B#11n", "B#12");
        assertThat(store.lastPositions).hasSize(4);
        // A neighbour has the audience of its own document.
        store.general = List.of(match(4, 1, .9, null, null, null, 101, 4, "S"));
        store.around = List.of(match(5, 1, 0, P1, null, null, 101, 5, "S"));
        assertThat(s.studentSearch("q", SCOPE, new StudentSearchOptions(4, 0, 1)).get(1).audience()).isEqualTo(PROGRAM);
    }

    @Test
    void optionsAndOldCalls() {
        for (int[] bad : new int[][] {{0, 0, 0}, {11, 0, 0}, {4, -1, 0}, {4, 11, 0}, {4, 0, -1}, {4, 0, 5}})
            assertThatThrownBy(() -> new StudentSearchOptions(bad[0], bad[1], bad[2])).isInstanceOf(IllegalArgumentException.class);
        exampleOf7_3(.63, .58);
        var s = service(.5, 3);
        var old = s.studentSearch("q", SCOPE, 4);
        assertThat(store.searches).isEqualTo(1);
        assertThat(store.findCalls).isZero();
        var expected = KnowledgeSearchServiceImpl.select(store.general, 4, .5, 3);
        assertThat(old).extracting(KnowledgeSearchHit::rank).containsExactlyElementsOf(expected.stream().map(Ranked::rank).toList());
        assertThat(old).extracting(KnowledgeSearchHit::score).containsExactlyElementsOf(expected.stream().map(Ranked::score).toList());
        assertThat(ids(old)).containsExactly(1L, 2L, 3L, 4L);
        assertThat(old).allSatisfy(h -> {
            assertThat(h.audience()).isEqualTo(UNIVERSITY);
            assertThat(h.neighbor()).isFalse();
        });
    }
}
