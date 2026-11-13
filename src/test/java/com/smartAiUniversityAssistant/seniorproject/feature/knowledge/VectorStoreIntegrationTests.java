package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** Spec file 51, section 15.1: the pgvector store with rows inserted by SQL. */
class VectorStoreIntegrationTests extends IngestionTestSupport {
    static final String MODEL = DeterministicEmbeddingClient.MODEL;

    static String unit(int hot) {
        return IntStream.range(0, 768).mapToObj(i -> i == hot ? "1" : "0").collect(Collectors.joining(",", "[", "]"));
    }

    long doc(String title, String scopeColumn, Long scopeId) { return document(title, categoryId, "2026-2027", scopeColumn, scopeId, null); }

    long file(long documentId, int revision, boolean current, String status) { return revision(documentId, revision, current, status); }

    void pagesAndChunks(long fileId, int pages, int chunks, String model) {
        for (int p = 1; p <= pages; p++) insertPage(fileId, p, "page " + p);
        for (int c = 0; c < chunks; c++) {
            insertChunk(fileId, c, "chunk " + c);
            if (model != null)
                db.update("UPDATE academic_document_chunks SET embedding=CAST(? AS vector), embedding_model=?, embedded_at=now() WHERE file_id=? AND chunk_index=?",
                        unit(c % 768), model, fileId, c);
        }
    }

    @Test
    void countByFileAndDeleteByFile() {
        long file = file(doc("A", null, null), 1, true, "EMBEDDING");
        pagesAndChunks(file, 3, 5, MODEL);
        db.update("UPDATE academic_document_chunks SET embedding_model='other-model' WHERE file_id=? AND chunk_index=4", file);
        db.update("UPDATE academic_document_chunks SET page_end=2 WHERE file_id=? AND chunk_index=3", file);
        assertThat(vectorStore.countByFile(file, MODEL)).isEqualTo(new FileIndexCounts(5, 4, 4, 2, 3));
        assertThat(vectorStore.countByFile(file, null).embeddedWithModel()).isZero();
        long other = file(doc("B", null, null), 1, true, "READY");
        pagesAndChunks(other, 2, 2, MODEL);
        assertThat(vectorStore.deleteByFile(file, 3)).isEqualTo(3);
        assertThat(chunks(file)).isEqualTo(2);
        assertThat(pages(file)).isEqualTo(3);
        assertThat(vectorStore.deleteByFile(file, 3)).isEqualTo(5);   // the last 2 chunks, then 3 pages
        assertThat(vectorStore.deleteByFile(file, 3)).isZero();
        assertThat(chunks(other) + pages(other)).isEqualTo(4);
        assertThat(vectorStore.countByFile(file, MODEL)).isEqualTo(new FileIndexCounts(0, 0, -1, 0, 0));
    }

    @Test
    void removeOutdatedRemovesOnlyOutdatedData() {
        long deletedDoc = doc("Deleted", null, null);
        long deletedFile = file(deletedDoc, 1, true, "READY");
        pagesAndChunks(deletedFile, 1, 2, MODEL);
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", deletedDoc);
        long replaced = doc("Replaced", null, null);
        long oldRevision = file(replaced, 1, false, "READY");
        pagesAndChunks(oldRevision, 1, 2, MODEL);
        long newRevision = file(replaced, 2, true, "READY");
        pagesAndChunks(newRevision, 1, 2, MODEL);
        long pendingDoc = doc("Pending", null, null);
        long keptOld = file(pendingDoc, 1, false, "READY");
        pagesAndChunks(keptOld, 1, 2, MODEL);
        long notReady = file(pendingDoc, 2, true, "EMBEDDING");
        pagesAndChunks(notReady, 1, 1, null);
        long archived = doc("Archived", null, null);
        long archivedFile = file(archived, 1, true, "READY");
        pagesAndChunks(archivedFile, 1, 2, MODEL);
        db.update("UPDATE academic_documents SET is_active=FALSE WHERE id=?", archived);
        assertThat(vectorStore.statistics(MODEL).outdatedChunks()).isEqualTo(4);

        assertThat(vectorStore.removeOutdated(1000)).isEqualTo(6);
        assertThat(chunks(deletedFile) + pages(deletedFile) + chunks(oldRevision) + pages(oldRevision)).isZero();
        assertThat(chunks(newRevision)).isEqualTo(2);
        assertThat(chunks(keptOld)).isEqualTo(2);
        assertThat(chunks(notReady)).isEqualTo(1);
        assertThat(chunks(archivedFile)).isEqualTo(2);
        assertThat(vectorStore.removeOutdated(1000)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM academic_document_files", Long.class)).isEqualTo(6);
    }

    @Test
    void removeOutdatedRespectsTheRowBudget() {
        long d = doc("Big", null, null);
        long f = file(d, 1, true, "READY");
        pagesAndChunks(f, 3, 7, MODEL);
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", d);
        assertThat(vectorStore.removeOutdated(4)).isEqualTo(4);
        assertThat(vectorStore.removeOutdated(1000)).isEqualTo(6);
    }

    @Test
    void statisticsMatchAKnownSet() {
        long ready = file(doc("Ready", null, null), 1, true, "READY");
        pagesAndChunks(ready, 2, 3, MODEL);
        long stale = file(doc("Stale", null, null), 1, true, "READY");
        pagesAndChunks(stale, 1, 2, "older-model");
        long archivedDoc = doc("Archived", null, null);
        long archived = file(archivedDoc, 1, true, "READY");
        pagesAndChunks(archived, 1, 1, MODEL);
        db.update("UPDATE academic_documents SET is_active=FALSE WHERE id=?", archivedDoc);
        long deletedDoc = doc("Deleted", null, null);
        long deleted = file(deletedDoc, 1, true, "READY");
        pagesAndChunks(deleted, 1, 1, MODEL);
        db.update("UPDATE academic_documents SET is_deleted=TRUE WHERE id=?", deletedDoc);
        long curriculum = curriculum(programA, "OLD-CUR", "ARCHIVED");
        long linked = file(doc("Old curriculum", "curriculum_version_id", curriculum), 1, true, "READY");
        pagesAndChunks(linked, 1, 2, MODEL);
        long uploaded = file(doc("Uploaded", null, null), 1, true, "UPLOADED");
        pagesAndChunks(uploaded, 1, 1, null);
        var s = vectorStore.statistics(MODEL);
        assertThat(s.readyDocuments()).isEqualTo(4);          // ready, stale, archived, old curriculum
        assertThat(s.staleDocuments()).isEqualTo(1);
        assertThat(s.searchableDocuments()).isEqualTo(1);
        assertThat(s.totalChunks()).isEqualTo(10);
        assertThat(s.searchableChunks()).isEqualTo(3);
        assertThat(s.chunksOfOtherModels()).isEqualTo(2);
        assertThat(s.outdatedChunks()).isEqualTo(1);          // the deleted document
        assertThat(s.totalPages()).isEqualTo(7);
        assertThat(s.chunkTableBytes()).isPositive();
        assertThat(s.vectorIndexBytes()).isPositive();
        assertThat(vectorStore.statistics(null).staleDocuments()).isEqualTo(4);
    }
}
