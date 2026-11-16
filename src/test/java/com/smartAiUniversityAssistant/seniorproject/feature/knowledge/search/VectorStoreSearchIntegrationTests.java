package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeSearchProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.IngestionTestSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.PgVectorKnowledgeVectorStore;
import java.time.Duration;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.*;

/** Spec file 52, section 17.3: chunks with chosen vectors, so order and scores are exact. */
class VectorStoreSearchIntegrationTests extends IngestionTestSupport {
    static final String MODEL = "search-model";
    @Autowired NamedParameterJdbcTemplate named;

    static float[] unit(int hot) { float[] v = new float[768]; v[hot] = 1; return v; }
    static float[] mixed(int a, int b) { float[] v = new float[768]; v[a] = (float) Math.sqrt(0.5); v[b] = (float) Math.sqrt(0.5); return v; }

    long readyDoc(String title, String scopeColumn, Long scopeId, float[]... vectors) {
        long doc = document(title, categoryId, "2026-2027", scopeColumn, scopeId, "READY");
        long file = currentFile(doc);
        for (int i = 0; i < vectors.length; i++) chunk(file, i, title + " chunk " + i, vectors[i], MODEL);
        return doc;
    }

    void chunk(long file, int index, String text, float[] vector, String model) {
        insertChunk(file, index, text);
        if (vector != null)
            db.update("UPDATE academic_document_chunks SET embedding=CAST(? AS vector), embedding_model=?, embedded_at=now() WHERE file_id=? AND chunk_index=?",
                    PgVectorKnowledgeVectorStore.literal(vector), model, file, index);
    }

    List<KnowledgeChunkMatch> search(KnowledgeSearchFilter filter) { return vectorStore.search(unit(0), MODEL, filter, 50); }

    Set<String> titles(KnowledgeSearchFilter filter) { return search(filter).stream().map(KnowledgeChunkMatch::title).collect(Collectors.toSet()); }

    @Test
    void orderLimitAndDistances() {
        readyDoc("A", null, null, unit(1), mixed(0, 1), unit(0), unit(0));
        var matches = search(null);
        assertThat(matches).extracting(KnowledgeChunkMatch::chunkIndex).containsExactly(2, 3, 1, 0);
        assertThat(matches.get(0).distance()).isCloseTo(0, within(1e-6));
        assertThat(matches.get(1).chunkId()).isGreaterThan(matches.get(0).chunkId());
        assertThat(matches.get(2).distance()).isCloseTo(1 - Math.sqrt(0.5), within(1e-6));
        assertThat(matches.get(3).distance()).isCloseTo(1, within(1e-6));
        assertThat(vectorStore.search(unit(0), MODEL, null, 2)).hasSize(2);
        assertThat(matches.get(0).categoryCode()).isEqualTo("REGULATION");
        assertThat(matches.get(0).fileExtension()).isEqualTo("pdf");
    }

    @Test
    void onlyEligibleChunksAreReturned() {
        long visible = readyDoc("Visible", null, null, unit(0));
        long file = currentFile(visible);
        chunk(file, 1, "no vector", null, null);
        chunk(file, 2, "other model", unit(0), "other-model");
        long replaced = readyDoc("Old revision", null, null, unit(0));
        db.update("UPDATE academic_document_files SET is_current=FALSE WHERE document_id=?", replaced);
        revision(replaced, 2, true, "EMBEDDING");
        for (String status : List.of("UPLOADED", "QUEUED", "PROCESSING", "CHUNKING", "EMBEDDING", "FAILED")) {
            long doc = readyDoc("Status " + status, null, null, unit(0));
            db.update("UPDATE academic_document_processing SET status=? WHERE file_id=?", status, currentFile(doc));
        }
        long archived = readyDoc("Archived", null, null, unit(0));
        db.update("UPDATE academic_documents SET is_active=FALSE WHERE id=?", archived);
        long deleted = readyDoc("Deleted", null, null, unit(0));
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", deleted);
        readyDoc("Archived curriculum", "curriculum_version_id", curriculum(programA, "OLD", "ARCHIVED"), unit(0));
        readyDoc("Active curriculum", "curriculum_version_id", curriculum(programA, "NEW", "ACTIVE"), unit(0));
        long inactiveCategory = category("OLD_CAT", "Old category");
        long otherCategory = document("Inactive category", inactiveCategory, "2026-2027", null, null, "READY");
        chunk(currentFile(otherCategory), 0, "c", unit(0), MODEL);
        db.update("UPDATE document_categories SET is_active=FALSE WHERE id=?", inactiveCategory);
        var matches = search(null);
        assertThat(matches).extracting(KnowledgeChunkMatch::title).containsExactlyInAnyOrder("Visible", "Active curriculum", "Inactive category");
        assertThat(matches.stream().filter(m -> m.title().equals("Visible"))).singleElement()
                .satisfies(m -> assertThat(m.chunkIndex()).isZero());
    }

    @Test
    void studentScope() {
        // programA belongs to departmentA (D1), programB to departmentB (D2)
        readyDoc("University", null, null, unit(0));
        readyDoc("Department D1", "department_id", departmentA, unit(0));
        readyDoc("Department D2", "department_id", departmentB, unit(0));
        readyDoc("Program P1", "program_id", programA, unit(0));
        readyDoc("Program P2", "program_id", programB, unit(0));
        readyDoc("Curriculum P1", "curriculum_version_id", curriculum(programA, "P1-CUR", "ACTIVE"), unit(0));
        readyDoc("Curriculum P2", "curriculum_version_id", curriculum(programB, "P2-CUR", "ACTIVE"), unit(0));
        assertThat(titles(new KnowledgeSearchFilter(new StudentKnowledgeScope(programA, departmentA), null, null, null)))
                .containsExactlyInAnyOrder("University", "Department D1", "Program P1", "Curriculum P1");
        assertThat(titles(new KnowledgeSearchFilter(new StudentKnowledgeScope(null, null), null, null, null))).containsExactly("University");
        assertThat(titles(new KnowledgeSearchFilter(new StudentKnowledgeScope(programA, null), null, null, null)))
                .containsExactlyInAnyOrder("University", "Department D1", "Program P1", "Curriculum P1");
        assertThat(titles(new KnowledgeSearchFilter(new StudentKnowledgeScope(null, departmentB), null, null, null)))
                .containsExactlyInAnyOrder("University", "Department D2");
        assertThat(titles(null)).hasSize(7);
    }

    @Test
    void filters() {
        long a = readyDoc("A", null, null, unit(0));
        long otherCategory = category("FORMS", "Forms");
        long b = document("B", otherCategory, "2025-2026", null, null, "READY");
        chunk(currentFile(b), 0, "b", unit(0), MODEL);
        assertThat(titles(new KnowledgeSearchFilter(null, otherCategory, null, null))).containsExactly("B");
        assertThat(titles(new KnowledgeSearchFilter(null, null, "2026-2027", null))).containsExactly("A");
        assertThat(titles(new KnowledgeSearchFilter(null, null, null, a))).containsExactly("A");
        assertThat(titles(new KnowledgeSearchFilter(null, otherCategory, "2025-2026", b))).containsExactly("B");
        assertThat(titles(new KnowledgeSearchFilter(null, otherCategory, "2026-2027", b))).isEmpty();
        assertThat(titles(new KnowledgeSearchFilter(null, 999_999L, null, null))).isEmpty();
    }

    @Test
    void settingsNeverStayOnPooledConnections() {
        readyDoc("A", null, null, unit(0), unit(1));
        var store = new PgVectorKnowledgeVectorStore(named, transactions, clock,
                new KnowledgeSearchProperties(5, 0.5, 3, 123, Duration.ofHours(1)));
        for (int i = 0; i < 20; i++) {
            assertThat(store.search(unit(0), MODEL, new KnowledgeSearchFilter(new StudentKnowledgeScope(null, null), null, null, null), 5))
                    .hasSize(2);
            assertThat(db.queryForObject("SELECT current_setting('hnsw.ef_search')", String.class)).isNotEqualTo("123");
            assertThat(db.queryForObject("SELECT current_setting('hnsw.iterative_scan')", String.class)).isNotEqualTo("relaxed_order");
        }
    }
}
