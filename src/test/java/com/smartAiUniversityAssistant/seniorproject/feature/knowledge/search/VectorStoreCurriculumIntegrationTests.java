package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeSearchProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.IngestionTestSupport;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.PgVectorKnowledgeVectorStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Spec file 54, section 14.2: curriculum_program_id, programDocumentsOnly and findChunks on PostgreSQL. */
class VectorStoreCurriculumIntegrationTests extends IngestionTestSupport {
    static final String MODEL = "search-model";
    @Autowired NamedParameterJdbcTemplate named;

    static float[] unit(int hot) { float[] v = new float[768]; v[hot] = 1; return v; }

    long readyDoc(String title, String scopeColumn, Long scopeId, int chunks) {
        long doc = document(title, categoryId, "2026-2027", scopeColumn, scopeId, "READY");
        long file = currentFile(doc);
        for (int i = 0; i < chunks; i++) chunk(file, i, title + " chunk " + i, MODEL);
        return doc;
    }

    void chunk(long file, int index, String text, String model) {
        insertChunk(file, index, text);
        db.update("UPDATE academic_document_chunks SET embedding=CAST(? AS vector), embedding_model=?, embedded_at=now() WHERE file_id=? AND chunk_index=?",
                PgVectorKnowledgeVectorStore.literal(unit(0)), model, file, index);
    }

    Set<String> titles(KnowledgeSearchFilter filter) {
        return vectorStore.search(unit(0), MODEL, filter, 50).stream().map(KnowledgeChunkMatch::title).collect(Collectors.toSet());
    }

    @Test
    void curriculumProgramIdAndProgramDocumentsOnly() {
        readyDoc("University", null, null, 1);
        readyDoc("Department D1", "department_id", departmentA, 1);
        readyDoc("Program P1", "program_id", programA, 1);
        readyDoc("Program P2", "program_id", programB, 1);
        readyDoc("Curriculum P1", "curriculum_version_id", curriculum(programA, "P1-CUR", "ACTIVE"), 1);
        readyDoc("Curriculum P2", "curriculum_version_id", curriculum(programB, "P2-CUR", "ACTIVE"), 1);
        var all = vectorStore.search(unit(0), MODEL, null, 50);
        assertThat(all).hasSize(6).allSatisfy(m -> {
            if (m.title().equals("Curriculum P1")) assertThat(m.curriculumProgramId()).isEqualTo(programA);
            else if (m.title().equals("Curriculum P2")) assertThat(m.curriculumProgramId()).isEqualTo(programB);
            else assertThat(m.curriculumProgramId()).isNull();
        });
        var scope = new StudentKnowledgeScope(programA, departmentA);
        assertThat(titles(new KnowledgeSearchFilter(null, null, null, null).programDocuments(programA)))
                .containsExactlyInAnyOrder("Program P1", "Curriculum P1");
        assertThat(titles(new KnowledgeSearchFilter(scope, null, null, null).programDocuments(programA)))
                .containsExactlyInAnyOrder("Program P1", "Curriculum P1");
        // Combined with the scope: a Student of P2 never reaches P1 documents through the program limit.
        assertThat(titles(new KnowledgeSearchFilter(new StudentKnowledgeScope(programB, departmentB), null, null, null).programDocuments(programA)))
                .isEmpty();
        long otherCategory = category("CURR", "Curriculum");
        long c = document("Other category P1", otherCategory, "2025-2026", "program_id", programA, "READY");
        chunk(currentFile(c), 0, "x", MODEL);
        assertThat(titles(new KnowledgeSearchFilter(scope, otherCategory, null, null).programDocuments(programA))).containsExactly("Other category P1");
        assertThat(titles(new KnowledgeSearchFilter(scope, null, "2025-2026", null).programDocuments(programA))).containsExactly("Other category P1");
        assertThat(titles(new KnowledgeSearchFilter(scope, null, null, c).programDocuments(programA))).containsExactly("Other category P1");
    }

    List<KnowledgeChunkPosition> positions(long file, int... indexes) {
        return IntStream.of(indexes).mapToObj(i -> new KnowledgeChunkPosition(file, i)).toList();
    }

    @Test
    void findChunksReturnsEligibleChunksInOrder() {
        long visible = readyDoc("Visible", null, null, 3);
        long file = currentFile(visible);
        var found = vectorStore.findChunks(MODEL, positions(file, 2, 0, 7));
        assertThat(found).extracting(KnowledgeChunkMatch::chunkIndex).containsExactly(0, 2);
        assertThat(found.get(0).title()).isEqualTo("Visible");
        assertThat(found.get(0).categoryCode()).isEqualTo("REGULATION");
        assertThat(found.get(0).distance()).isZero();

        long archived = readyDoc("Archived", null, null, 1);
        db.update("UPDATE academic_documents SET is_active=FALSE WHERE id=?", archived);
        long deleted = readyDoc("Deleted", null, null, 1);
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", deleted);
        long replaced = readyDoc("Replaced", null, null, 1);
        long replacedFile = currentFile(replaced);
        db.update("UPDATE academic_document_files SET is_current=FALSE WHERE document_id=?", replaced);
        revision(replaced, 2, true, "READY");
        long notReady = readyDoc("Not ready", null, null, 1);
        db.update("UPDATE academic_document_processing SET status='EMBEDDING' WHERE file_id=?", currentFile(notReady));
        long oldCurriculum = readyDoc("Archived curriculum", "curriculum_version_id", curriculum(programA, "OLD", "ARCHIVED"), 1);
        long otherModel = document("Other model", categoryId, "2026-2027", null, null, "READY");
        chunk(currentFile(otherModel), 0, "o", "other-model");
        for (long fileId : List.of(currentFile(archived), currentFile(deleted), replacedFile, currentFile(notReady),
                currentFile(oldCurriculum), currentFile(otherModel)))
            assertThat(vectorStore.findChunks(MODEL, positions(fileId, 0))).as("file " + fileId).isEmpty();
    }

    @Test
    void oneStatementForTwentyPositionsAndNoneForEmpty() {
        long doc = readyDoc("Many", null, null, 20);
        var statements = new AtomicInteger();
        var counting = new NamedParameterJdbcTemplate(named.getJdbcTemplate()) {
            @Override
            public <T> List<T> query(String sql, SqlParameterSource params, RowMapper<T> rowMapper) {
                statements.incrementAndGet();
                return super.query(sql, params, rowMapper);
            }
        };
        var store = new PgVectorKnowledgeVectorStore(counting, transactions, clock, new KnowledgeSearchProperties(5, 0.5, 3, 100, Duration.ofHours(1)));
        var all = IntStream.range(0, 20).toArray();
        assertThat(store.findChunks(MODEL, positions(currentFile(doc), all))).hasSize(20);
        assertThat(statements).hasValue(1);
        assertThat(store.findChunks(MODEL, List.of())).isEmpty();
        assertThat(statements).hasValue(1);
    }
}
