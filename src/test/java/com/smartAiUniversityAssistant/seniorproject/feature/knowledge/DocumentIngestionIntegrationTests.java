package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeIngestionException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spec file 46, section 20.2: the worker driven with fake stages through the real upload and reprocess endpoints. */
class DocumentIngestionIntegrationTests extends IngestionTestSupport {

    @Test
    void fiveSucceedingStagesMakeAnUploadReady() throws Exception {
        var worker = worker(allFakes());
        long doc = uploadText("Article 1. Text.");
        long file = currentFile(doc);
        assertThat(statusOf(file)).isEqualTo("UPLOADED");
        worker.runOnce();
        assertThat(calls).containsExactly("EXTRACT:" + file, "CLEAN:" + file, "CHUNK:" + file, "EMBED:" + file, "INDEX:" + file);
        var row = processingRow(file);
        assertThat(row).containsEntry("status", "READY").containsEntry("attempt_count", 1).containsEntry("queued_by", actorId)
                .containsEntry("failed_stage", null).containsEntry("last_error_code", null).containsEntry("next_attempt_at", null);
        assertThat(row.get("started_at")).isNotNull();
        assertThat(row.get("completed_at")).isNotNull();
        assertThat(row.get("queued_at")).isNotNull();
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("READY")).andExpect(jsonPath("ingestionAvailable").value(true))
                .andExpect(jsonPath("message").value("Indexed and ready for AI retrieval."))
                .andExpect(jsonPath("queuedAt").exists()).andExpect(jsonPath("failedStage").doesNotExist());
    }

    @Test
    void incompletePipelineDoesNotAutoQueueAndStopsAtTheMissingStage() throws Exception {
        var worker = worker(List.of(fake(EXTRACT, (job, control) -> insertPage(job.fileId(), 1, "page text"))));
        long doc = uploadText("text");
        long file = currentFile(doc);
        worker.runOnce();
        assertThat(statusOf(file)).isEqualTo("UPLOADED");
        assertThat(calls).isEmpty();
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("ingestionAvailable").value(false))
                .andExpect(jsonPath("message").value("Awaiting AI processing. Document ingestion is not available yet."));
        reprocess(doc, null).andExpect(status().isAccepted()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("status").value("QUEUED")).andExpect(jsonPath("message").value("Queued for AI processing."));
        worker.runOnce();
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("failed_stage", "CLEAN")
                .containsEntry("last_error_code", "INGESTION_STAGE_NOT_AVAILABLE").containsEntry("next_attempt_at", null);
        assertThat(pages(file)).isOne();
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("pageCount").value(1))
                .andExpect(jsonPath("failedStage").value("CLEAN")).andExpect(jsonPath("message").value("Processing failed."));
    }

    @Test
    void retryableFailureIsRetriedWithGrowingDelayUntilMaxAttempts() throws Exception {
        var stages = new ArrayList<>(allFakes());
        stages.set(3, fake(EMBED, (job, control) -> {
            throw new KnowledgeIngestionException(EMBED, "EMBEDDING_RATE_LIMITED", true, "The embedding provider is rate limiting requests.");
        }));
        var worker = worker(properties(true, true, 2, Duration.ofMinutes(30)), stages);
        long doc = uploadText("text");
        long file = currentFile(doc);
        worker.runOnce();
        var row = processingRow(file);
        assertThat(row).containsEntry("status", "FAILED").containsEntry("failed_stage", "EMBED")
                .containsEntry("last_error_code", "EMBEDDING_RATE_LIMITED").containsEntry("attempt_count", 1)
                .containsEntry("last_error_message", "The embedding provider is rate limiting requests.");
        assertThat(row.get("next_attempt_at")).isEqualTo(java.sql.Timestamp.valueOf(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).plusMinutes(2)));
        perform(get(BASE + "/" + doc + "/processing"), null)
                .andExpect(jsonPath("message").value("Processing failed. It will be retried automatically."))
                .andExpect(jsonPath("nextAttemptAt").exists());
        calls.clear();
        worker.runOnce();
        assertThat(calls).isEmpty();
        clock.advance(Duration.ofMinutes(2).plusSeconds(1));
        worker.runOnce();
        // The retry resumes at EMBED: no chunks exist, so it runs from EXTRACT again.
        assertThat(calls).first().isEqualTo("EXTRACT:" + file);
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("attempt_count", 2)
                .containsEntry("next_attempt_at", null);
    }

    @Test
    void nonRetryableAndUnexpectedFailures() throws Exception {
        var stages = new ArrayList<>(allFakes());
        stages.set(0, fake(EXTRACT, (job, control) -> {
            throw new KnowledgeIngestionException(EXTRACT, "DOCUMENT_ENCRYPTED", false, "The document is password-protected.");
        }));
        var worker = worker(stages);
        long first = currentFile(uploadText("one"));
        worker.runOnce();
        assertThat(processingRow(first)).containsEntry("status", "FAILED").containsEntry("failed_stage", "EXTRACT")
                .containsEntry("last_error_code", "DOCUMENT_ENCRYPTED").containsEntry("next_attempt_at", null);

        stages.set(0, fake(EXTRACT, (job, control) -> { throw new IllegalStateException("secret document text /var/storage/x.pdf"); }));
        worker = worker(stages);
        long second = currentFile(uploadText("two"));
        worker.runOnce();
        var row = processingRow(second);
        assertThat(row).containsEntry("status", "FAILED").containsEntry("last_error_code", "INGESTION_INTERNAL_ERROR")
                .containsEntry("failed_stage", "EXTRACT");
        assertThat(row.get("next_attempt_at")).isNotNull();
        assertThat((String) row.get("last_error_message")).doesNotContain("secret").doesNotContain("/var/storage");
    }

    @Test
    void stageTimeoutIsARetryableFailure() throws Exception {
        var stages = new ArrayList<>(allFakes());
        stages.set(2, fake(CHUNK, (job, control) -> {
            clock.advance(Duration.ofMinutes(31));
            control.checkpoint();
        }));
        var worker = worker(stages);
        long file = currentFile(uploadText("text"));
        worker.runOnce();
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("failed_stage", "CHUNK")
                .containsEntry("last_error_code", "INGESTION_STAGE_TIMEOUT");
        assertThat(processingRow(file).get("next_attempt_at")).isNotNull();
    }

    @Test
    void resumeAtEmbedOnlyWhenChunksExist() throws Exception {
        var failEmbed = new boolean[] {true};
        var stages = new ArrayList<>(allFakes());
        stages.set(2, fake(CHUNK, (job, control) -> insertChunk(job.fileId(), 0, "chunk")));
        stages.set(3, fake(EMBED, (job, control) -> {
            if (failEmbed[0]) throw new KnowledgeIngestionException(EMBED, "EMBEDDING_AUTHENTICATION_FAILED", false, "rejected");
        }));
        var worker = worker(stages);
        long doc = uploadText("text");
        long file = currentFile(doc);
        worker.runOnce();
        assertThat(processingRow(file)).containsEntry("failed_stage", "EMBED");
        long chunkId = db.queryForObject("SELECT id FROM academic_document_chunks WHERE file_id=?", Long.class, file);
        failEmbed[0] = false;
        calls.clear();
        reprocess(doc, null).andExpect(status().isAccepted()).andExpect(jsonPath("failedStage").value("EMBED"));
        worker.runOnce();
        assertThat(calls).containsExactly("EMBED:" + file, "INDEX:" + file);
        assertThat(statusOf(file)).isEqualTo("READY");
        assertThat(db.queryForObject("SELECT id FROM academic_document_chunks WHERE file_id=?", Long.class, file)).isEqualTo(chunkId);

        // Without chunks the resume hint is ignored and the run starts at EXTRACT.
        db.update("DELETE FROM academic_document_chunks WHERE file_id=?", file);
        db.update("UPDATE academic_document_processing SET status='FAILED', failed_stage='EMBED' WHERE file_id=?", file);
        calls.clear();
        reprocess(doc, "{\"mode\":\"RESUME\"}").andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(calls).first().isEqualTo("EXTRACT:" + file);
    }

    @Test
    void fullRunDeletesEarlierPagesAndChunksFirst() throws Exception {
        var seen = new long[2];
        var stages = new ArrayList<>(allFakes());
        stages.set(0, fake(EXTRACT, (job, control) -> {
            seen[0] = pages(job.fileId());
            seen[1] = chunks(job.fileId());
        }));
        var worker = worker(properties(true, false, 4, Duration.ofMinutes(30)), stages);
        long doc = uploadText("text");
        long file = currentFile(doc);
        for (int i = 1; i <= 3; i++) insertPage(file, i, "old page");
        for (int i = 0; i < 3; i++) insertChunk(file, i, "old chunk");
        reprocess(doc, "{\"mode\":\"FULL\"}").andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(seen).containsExactly(0, 0);
        assertThat(statusOf(file)).isEqualTo("READY");
    }

    @Test
    void reprocessMatrix() throws Exception {
        worker(properties(true, false, 4, Duration.ofMinutes(30)), allFakes());
        long doc = uploadText("text");
        long file = currentFile(doc);
        record Case(String status, String mode, int http, String code, String expectedFailedStage) {}
        var cases = List.of(
                new Case("UPLOADED", "RESUME", 202, null, null), new Case("UPLOADED", "FULL", 202, null, null),
                new Case("FAILED", "RESUME", 202, null, "CHUNK"), new Case("FAILED", "FULL", 202, null, null),
                new Case("READY", "RESUME", 409, "DOCUMENT_ALREADY_PROCESSED", null), new Case("READY", "FULL", 202, null, null),
                new Case("QUEUED", "RESUME", 409, "DOCUMENT_PROCESSING_ALREADY_RUNNING", null),
                new Case("QUEUED", "FULL", 409, "DOCUMENT_PROCESSING_ALREADY_RUNNING", null),
                new Case("PROCESSING", "RESUME", 409, "DOCUMENT_PROCESSING_ALREADY_RUNNING", null),
                new Case("CHUNKING", "FULL", 409, "DOCUMENT_PROCESSING_ALREADY_RUNNING", null),
                new Case("EMBEDDING", "RESUME", 409, "DOCUMENT_PROCESSING_ALREADY_RUNNING", null),
                new Case("EMBEDDING", "FULL", 409, "DOCUMENT_PROCESSING_ALREADY_RUNNING", null));
        long otherAdmin = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('KNW-OTHER','Other','Admin','knowledge.other@example.test','ACTIVE') RETURNING id""", Long.class);
        for (Case c : cases) {
            db.update("""
                    UPDATE academic_document_processing SET status=?, failed_stage=?, attempt_count=3, queued_by=?,
                        last_error_code=?, next_attempt_at=? WHERE file_id=?""",
                    c.status(), c.status().equals("FAILED") ? "CHUNK" : null, otherAdmin, c.status().equals("FAILED") ? "X" : null,
                    c.status().equals("FAILED") ? java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(1)) : null, file);
            var result = reprocess(doc, "{\"mode\":\"" + c.mode() + "\"}").andExpect(status().is(c.http()));
            var row = processingRow(file);
            if (c.http() == 202) {
                result.andExpect(jsonPath("status").value("QUEUED")).andExpect(jsonPath("attemptCount").value(0));
                assertThat(row).as(c.toString()).containsEntry("status", "QUEUED").containsEntry("attempt_count", 0)
                        .containsEntry("queued_by", actorId).containsEntry("failed_stage", c.expectedFailedStage())
                        .containsEntry("last_error_code", null).containsEntry("next_attempt_at", null);
                assertThat(row.get("queued_at")).isNotNull();
            } else {
                result.andExpect(jsonPath("code").value(c.code()));
                assertThat(row).as(c.toString()).containsEntry("status", c.status()).containsEntry("attempt_count", 3);
            }
        }
        db.update("UPDATE academic_document_processing SET queued_by=NULL WHERE file_id=?", file);
        db.update("DELETE FROM app_users WHERE id=?", otherAdmin);
    }

    @Test
    void reprocessValidationArchivedAndDeleted() throws Exception {
        worker(allFakes());
        long doc = uploadText("text");
        for (String body : List.of("{\"mode\":\"FAST\"}", "{\"mode\":\"FULL\",\"x\":1}", "\"FULL\"", "[]", "{\"mode\":1}", "{",
                "{\"mode\":\"full\"}", "{\"mode\":[\"FULL\"]}"))
            reprocess(doc, body).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        reprocess(doc, "{}").andExpect(status().isAccepted());
        perform(patch(BASE + "/" + doc + "/archive"), null).andExpect(status().isOk());
        db.update("UPDATE academic_document_processing SET status='FAILED' WHERE file_id=?", currentFile(doc));
        reprocess(doc, null).andExpect(status().isConflict()).andExpect(jsonPath("code").value("DOCUMENT_NOT_EDITABLE"));
        perform(patch(BASE + "/" + doc + "/activate"), null).andExpect(status().isOk());
        perform(delete(BASE + "/" + doc), null).andExpect(status().isNoContent());
        reprocess(doc, null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_NOT_FOUND"));
        reprocess(999_999, null).andExpect(status().isNotFound());
    }

    @Test
    void replacingTheFileCancelsQueuedAndRunningJobs() throws Exception {
        var worker = worker(properties(true, false, 4, Duration.ofMinutes(30)), allFakes());
        long doc = uploadText("v1");
        long first = currentFile(doc);
        reprocess(doc, null).andExpect(status().isAccepted());
        replaceFile(doc, "v2.txt", KnowledgeTestFiles.TXT, "v2".getBytes(StandardCharsets.UTF_8)).andExpect(status().isCreated());
        worker.runOnce();
        assertThat(processingRow(first)).containsEntry("status", "FAILED").containsEntry("last_error_code", "INGESTION_SUPERSEDED")
                .containsEntry("failed_stage", null).containsEntry("next_attempt_at", null);
        assertThat(calls).isEmpty();

        long second = currentFile(doc);
        var stages = new ArrayList<>(allFakes());
        stages.set(0, fake(EXTRACT, (job, control) -> {
            insertPage(job.fileId(), 1, "page");
            insertChunk(job.fileId(), 0, "chunk");
            try {
                replaceFile(doc, "v3.txt", KnowledgeTestFiles.TXT, "v3".getBytes(StandardCharsets.UTF_8)).andExpect(status().isCreated());
            } catch (Exception e) { throw new AssertionError(e); }
            control.checkpoint();
        }));
        worker = worker(properties(true, false, 4, Duration.ofMinutes(30)), stages);
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(processingRow(second)).containsEntry("status", "FAILED").containsEntry("last_error_code", "INGESTION_SUPERSEDED")
                .containsEntry("next_attempt_at", null);
        assertThat(pages(second)).isZero();
        assertThat(chunks(second)).isZero();
        assertThat(calls).containsExactly("EXTRACT:" + second);
    }

    @Test
    void interruptedJobIsRecoveredAndRetried() throws Exception {
        var worker = worker(properties(true, false, 4, Duration.ofMinutes(30)), allFakes());
        long doc = uploadText("text");
        long file = currentFile(doc);
        insertChunk(file, 0, "chunk");
        db.update("UPDATE academic_document_processing SET status='EMBEDDING', attempt_count=1, updated_at=? WHERE file_id=?",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMinutes(16), file);
        worker.runOnce();
        // Recovered (INGESTION_INTERRUPTED at EMBED, due now), queued again and resumed at EMBED in the same tick.
        assertThat(calls).containsExactly("EMBED:" + file, "INDEX:" + file);
        assertThat(processingRow(file)).containsEntry("status", "READY").containsEntry("attempt_count", 2);

        db.update("UPDATE academic_document_processing SET status='CHUNKING', attempt_count=4, updated_at=? WHERE file_id=?",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMinutes(16), file);
        worker.runOnce();
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("last_error_code", "INGESTION_INTERRUPTED")
                .containsEntry("failed_stage", "CHUNK").containsEntry("next_attempt_at", null);
    }

    @Test
    void twoWorkersProcessEachJobExactlyOnce() throws Exception {
        var barrier = new CyclicBarrier(2);
        var stages = new ArrayList<>(allFakes());
        stages.set(0, fake(EXTRACT, (job, control) -> {
            try { barrier.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new AssertionError(e); }
        }));
        var properties = properties(true, true, 4, Duration.ofMinutes(30));
        var a = newWorker(properties, stages);
        var b = newWorker(properties, stages);
        long first = currentFile(uploadText("one"));
        clock.advance(Duration.ofSeconds(1));
        long second = currentFile(uploadText("two"));
        var pool = Executors.newFixedThreadPool(2);
        try {
            var fa = pool.submit(a::runOnce);
            var fb = pool.submit(b::runOnce);
            fa.get(30, TimeUnit.SECONDS);
            fb.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(calls.stream().filter(c -> c.startsWith("EXTRACT:")).toList())
                .containsExactlyInAnyOrder("EXTRACT:" + first, "EXTRACT:" + second);
        assertThat(statusOf(first)).isEqualTo("READY");
        assertThat(statusOf(second)).isEqualTo("READY");
        assertThat(processingRow(first)).containsEntry("attempt_count", 1);
        assertThat(processingRow(second)).containsEntry("attempt_count", 1);
    }

    @Test
    void processingApiReturnsNewFieldsAndDerivedCounts() throws Exception {
        worker(List.of(fake(EXTRACT)));
        long doc = uploadText("text");
        long file = currentFile(doc);
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("UPLOADED")).andExpect(jsonPath("ingestionAvailable").value(false))
                .andExpect(jsonPath("failedStage").doesNotExist()).andExpect(jsonPath("pageCount").value(0))
                .andExpect(jsonPath("chunkCount").value(0)).andExpect(jsonPath("embeddedChunkCount").value(0));
        insertPage(file, 1, "a");
        insertPage(file, 2, "b");
        for (int i = 0; i < 3; i++) insertChunk(file, i, "c" + i);
        db.update("""
                UPDATE academic_document_chunks SET embedding=CAST(? AS vector), embedding_model='test-embedding-768', embedded_at=now()
                WHERE file_id=? AND chunk_index=0""", "[" + "0,".repeat(767) + "1]", file);
        db.update("UPDATE academic_document_processing SET status='EMBEDDING' WHERE file_id=?", file);
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("pageCount").value(2))
                .andExpect(jsonPath("chunkCount").value(3)).andExpect(jsonPath("embeddedChunkCount").value(1))
                .andExpect(jsonPath("message").value("Creating embeddings (1 of 3 chunks)."));
    }

    @Test
    void authorization() throws Exception {
        worker(List.of(fake(EXTRACT)));
        long doc = uploadText("text");
        mvc.perform(post(BASE + "/" + doc + "/reprocess")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + doc + "/processing")).andExpect(status().isUnauthorized());
        String student = studentToken("RAG-ING-1");
        mvc.perform(post(BASE + "/" + doc + "/reprocess").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + doc + "/processing").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            access = login();
            db.update("UPDATE academic_document_processing SET status='FAILED' WHERE file_id=?", currentFile(doc));
            reprocess(doc, null).andExpect(status().isAccepted());
            perform(get(BASE + "/" + doc + "/processing"), null).andExpect(status().isOk());
        }
    }
}
