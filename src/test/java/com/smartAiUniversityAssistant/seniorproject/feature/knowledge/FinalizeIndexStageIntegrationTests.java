package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeIngestionException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.FinalizeIndexStage;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec file 51, section 15.2: a worker built by hand with all five real stages. */
class FinalizeIndexStageIntegrationTests extends IngestionTestSupport {
    @TempDir Path temp;
    @Autowired NamedParameterJdbcTemplate named;

    FinalizeIndexStage indexStage() { return new FinalizeIndexStage(vectorStore, embeddingClient, named); }

    List<IngestionStage> all() {
        return List.of(extractStage(temp), cleanStage, chunkStage, embedStage(embeddingClient, 16, 8000), indexStage());
    }

    long upload(String text) throws Exception { return upload("chapters.txt", TXT, KnowledgeRealFiles.utf8(text)); }

    @Test
    void completeFileBecomesReady() throws Exception {
        var worker = worker(all());
        assertThat(worker.ingestionAvailable()).isTrue();
        long doc = upload(chapters(4, ""));
        worker.runOnce();   // queued automatically: the pipeline is complete
        long file = currentFile(doc);
        assertThat(processingRow(file)).containsEntry("status", "READY").containsEntry("failed_stage", null)
                .containsEntry("last_error_code", null).containsEntry("next_attempt_at", null);
        assertThat(processingRow(file).get("completed_at")).isNotNull();
    }

    @Test
    void missingVectorIsIncompleteAndResumesAtEmbed() throws Exception {
        var worker = worker(all());
        long doc = upload(chapters(4, ""));
        long file = currentFile(doc);
        worker.runOnce();
        db.update("UPDATE academic_document_chunks SET embedding=NULL, embedding_model=NULL, embedded_at=NULL WHERE file_id=? AND chunk_index=2", file);
        db.update("UPDATE academic_document_processing SET status='EMBEDDING' WHERE file_id=?", file);
        // Run INDEX alone on the damaged file.
        assertThatThrownBy(() -> indexStage().execute(job(doc, file), () -> {})).isInstanceOfSatisfying(KnowledgeIngestionException.class, e -> {
            assertThat(e.code()).isEqualTo("INDEX_INCOMPLETE");
            assertThat(e.retryable()).isTrue();
        });
        db.update("UPDATE academic_document_processing SET status='FAILED', failed_stage='INDEX' WHERE file_id=?", file);
        embeddingClient.reset();
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(embeddingClient.texts()).isEqualTo(1);
        assertThat(statusOf(file)).isEqualTo("READY");
    }

    @Test
    void gapsAndMissingChunksFailVerification() throws Exception {
        var worker = worker(all());
        long doc = upload(chapters(4, ""));
        long file = currentFile(doc);
        worker.runOnce();
        db.update("DELETE FROM academic_document_chunks WHERE file_id=? AND chunk_index=1", file);
        assertThatThrownBy(() -> indexStage().execute(job(doc, file), () -> {})).isInstanceOfSatisfying(KnowledgeIngestionException.class, e -> {
            assertThat(e.code()).isEqualTo("INDEX_VERIFICATION_FAILED");
            assertThat(e.retryable()).isFalse();
        });
        db.update("DELETE FROM academic_document_chunks WHERE file_id=?", file);
        assertThatThrownBy(() -> indexStage().execute(job(doc, file), () -> {})).isInstanceOfSatisfying(KnowledgeIngestionException.class,
                e -> assertThat(e.code()).isEqualTo("INDEX_INCOMPLETE"));
    }

    @Test
    void newRevisionRemovesTheOldDataOnlyAfterItIsVerified() throws Exception {
        var worker = worker(all());
        long doc = upload(chapters(5, ""));
        long first = currentFile(doc);
        worker.runOnce();
        assertThat(statusOf(first)).isEqualTo("READY");
        embeddingClient.reset();
        replaceFile(doc, "v2.txt", TXT, KnowledgeRealFiles.utf8(chapters(5, " (amended)"))).andExpect(status().isCreated());
        long second = currentFile(doc);
        worker.runOnce();
        assertThat(statusOf(second)).isEqualTo("READY");
        assertThat(embeddingClient.texts()).isEqualTo(1);       // reuse happened before the old data was removed
        assertThat(chunks(first) + pages(first)).isZero();
        assertThat(chunks(second)).isEqualTo(5);

        replaceFile(doc, "v3.txt", TXT, KnowledgeRealFiles.utf8("   \n")).andExpect(status().isCreated());
        long third = currentFile(doc);
        worker.runOnce();
        assertThat(processingRow(third)).containsEntry("last_error_code", "DOCUMENT_TEXT_EMPTY");
        assertThat(chunks(second)).isEqualTo(5);                // a failed revision leaves the old data in place
    }

    IngestionJob job(long doc, long file) {
        return new IngestionJob(0, doc, file, 1, "unused", "txt", "text/plain", 1, "x", 1, IngestionStageType.INDEX);
    }
}
