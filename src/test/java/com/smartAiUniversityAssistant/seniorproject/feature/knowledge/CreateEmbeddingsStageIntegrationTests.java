package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spec file 50, section 14.3: the real EXTRACT, CLEAN, CHUNK and EMBED stages with DeterministicEmbeddingClient. */
class CreateEmbeddingsStageIntegrationTests extends IngestionTestSupport {
    @TempDir Path temp;
    final DeterministicEmbeddingClient client = embeddingClient;

    List<IngestionStage> stages(int batchSize, int maxBatchTokens) {
        return List.of(extractStage(temp), cleanStage, chunkStage, embedStage(client, batchSize, maxBatchTokens));
    }

    long process(String text, int batchSize, int maxBatchTokens) throws Exception {
        var worker = worker(stages(batchSize, maxBatchTokens));
        long doc = upload("chapters.txt", TXT, KnowledgeRealFiles.utf8(text));
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        return doc;
    }

    long embedded(long fileId, String model) {
        return db.queryForObject("SELECT count(*) FROM academic_document_chunks WHERE file_id=? AND embedding IS NOT NULL AND embedding_model=?",
                Long.class, fileId, model);
    }

    @Test
    void everyChunkGetsAVectorInBatches() throws Exception {
        long doc = process(chapters(10, ""), 4, 8000);
        long file = currentFile(doc);
        assertThat(chunks(file)).isEqualTo(10);
        assertThat(client.batchSizes()).containsExactly(4, 4, 2);
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("failed_stage", "INDEX")
                .containsEntry("last_error_code", "INGESTION_STAGE_NOT_AVAILABLE");
        assertThat(db.queryForList("""
                SELECT vector_dims(embedding) AS dims, embedding_model, embedded_at FROM academic_document_chunks WHERE file_id=?""", file))
                .hasSize(10).allSatisfy(r -> {
                    assertThat(r).containsEntry("dims", 768).containsEntry("embedding_model", DeterministicEmbeddingClient.MODEL);
                    assertThat(r.get("embedded_at")).isNotNull();
                });
        // The stored vector is the vector of the embedding input that was sent (spec file 49, section 11).
        var row = db.queryForMap("SELECT section_title, content, embedding::text AS v FROM academic_document_chunks WHERE file_id=? AND chunk_index=3", file);
        float[] expected = DeterministicEmbeddingClient.vector(KnowledgeTextChunker.embeddingInput((String) row.get("section_title"), (String) row.get("content")));
        double[] stored = parseVector((String) row.get("v"));
        for (int i = 0; i < 768; i++) assertThat(stored[i]).isCloseTo(expected[i], within(1e-6));
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("chunkCount").value(10))
                .andExpect(jsonPath("embeddedChunkCount").value(10));
        perform(get(BASE + "/" + doc + "/chunks/3"), null).andExpect(jsonPath("embedded").value(true))
                .andExpect(jsonPath("embeddingModel").value(DeterministicEmbeddingClient.MODEL)).andExpect(jsonPath("embeddedAt").exists());

        client.reset();
        process(chapters(3, " low"), 16, 50);
        assertThat(client.batchSizes()).isNotEmpty().allSatisfy(n -> assertThat(n).isEqualTo(1));
    }

    @Test
    void retryableFailureKeepsSavedVectorsAndResumes() throws Exception {
        client.failCall(2, "EMBEDDING_RATE_LIMITED");
        long doc = process(chapters(10, ""), 4, 8000);
        long file = currentFile(doc);
        var row = processingRow(file);
        assertThat(row).containsEntry("status", "FAILED").containsEntry("failed_stage", "EMBED")
                .containsEntry("last_error_code", "EMBEDDING_RATE_LIMITED")
                .containsEntry("last_error_message", "The embedding provider is rate limiting requests.");
        assertThat(row.get("next_attempt_at")).isNotNull();
        assertThat(embedded(file, DeterministicEmbeddingClient.MODEL)).isEqualTo(4);
        client.reset();
        clock.advance(Duration.ofMinutes(3));
        switchable.runOnce();
        assertThat(client.texts()).isEqualTo(6);
        assertThat(embedded(file, DeterministicEmbeddingClient.MODEL)).isEqualTo(10);
        assertThat(processingRow(file)).containsEntry("failed_stage", "INDEX");

        client.reset();
        client.failAlways("EMBEDDING_AUTHENTICATION_FAILED");
        long other = process(chapters(2, " other"), 16, 8000);
        assertThat(processingRow(currentFile(other))).containsEntry("last_error_code", "EMBEDDING_AUTHENTICATION_FAILED")
                .containsEntry("failed_stage", "EMBED").containsEntry("next_attempt_at", null);
    }

    @Test
    void identicalPassagesReuseVectorsAcrossDocumentsAndRevisions() throws Exception {
        process(chapters(5, ""), 16, 8000);
        assertThat(client.texts()).isEqualTo(5);
        client.reset();
        long second = process(chapters(5, ""), 16, 8000);
        assertThat(client.calls()).isZero();
        assertThat(embedded(currentFile(second), DeterministicEmbeddingClient.MODEL)).isEqualTo(5);

        client.reset();
        replaceFile(second, "v2.txt", TXT, KnowledgeRealFiles.utf8(chapters(5, " (amended)"))).andExpect(status().isCreated());
        long revision = currentFile(second);
        reprocess(second, null).andExpect(status().isAccepted());
        switchable.runOnce();
        assertThat(client.texts()).isEqualTo(1);
        assertThat(embedded(revision, DeterministicEmbeddingClient.MODEL)).isEqualTo(5);
    }

    @Test
    void modelChangeOutdatedChunksAndNotConfigured() throws Exception {
        long doc = process(chapters(4, ""), 16, 8000);
        long file = currentFile(doc);
        var ids = db.queryForList("SELECT id FROM academic_document_chunks WHERE file_id=? ORDER BY id", Long.class, file);
        client.reset();
        client.model("test-embedding-B");
        reprocess(doc, null).andExpect(status().isAccepted());   // failed at INDEX: RESUME starts at EMBED
        switchable.runOnce();
        assertThat(client.texts()).isEqualTo(4);
        assertThat(embedded(file, "test-embedding-B")).isEqualTo(4);
        assertThat(db.queryForList("SELECT id FROM academic_document_chunks WHERE file_id=? ORDER BY id", Long.class, file)).isEqualTo(ids);

        db.update("UPDATE academic_document_chunks SET content = content || ' changed', embedding=NULL, embedding_model=NULL, embedded_at=NULL WHERE file_id=? AND chunk_index=1", file);
        reprocess(doc, null).andExpect(status().isAccepted());
        switchable.runOnce();
        assertThat(processingRow(file)).containsEntry("last_error_code", "DOCUMENT_CHUNKS_OUTDATED").containsEntry("failed_stage", "EMBED")
                .containsEntry("next_attempt_at", null);

        client.reset();
        client.configured(false);
        long unconfigured = process(chapters(1, " x"), 16, 8000);
        assertThat(processingRow(currentFile(unconfigured))).containsEntry("last_error_code", "EMBEDDING_NOT_CONFIGURED")
                .containsEntry("failed_stage", "EMBED").containsEntry("next_attempt_at", null);
    }

    @Test
    void replacingTheFileBetweenBatchesCancelsTheRun() throws Exception {
        var worker = worker(stages(4, 8000));
        long doc = upload("chapters.txt", TXT, KnowledgeRealFiles.utf8(chapters(10, "")));
        long file = currentFile(doc);
        var replaced = new boolean[1];
        client.onCall(text -> {
            if (client.calls() == 2 && !replaced[0]) {
                replaced[0] = true;
                try {
                    replaceFile(doc, "v2.txt", TXT, KnowledgeRealFiles.utf8("New text")).andExpect(status().isCreated());
                } catch (Exception e) { throw new AssertionError(e); }
            }
        });
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("last_error_code", "INGESTION_SUPERSEDED");
        assertThat(chunks(file)).isZero();
        assertThat(pages(file)).isZero();
        assertThat(client.documentCalls()).isEqualTo(2);
    }
}
