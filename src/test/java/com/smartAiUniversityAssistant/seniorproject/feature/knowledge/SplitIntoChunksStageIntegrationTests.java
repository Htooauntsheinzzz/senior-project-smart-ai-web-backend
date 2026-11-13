package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentChunkRepository;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spec file 49, sections 18.2 and 18.3: the real EXTRACT, CLEAN and CHUNK stages only, and the chunk endpoints. */
class SplitIntoChunksStageIntegrationTests extends IngestionTestSupport {
    @TempDir Path temp;
    @Autowired AcademicDocumentChunkRepository chunkRepository;

    long sample() throws Exception {
        var worker = worker(List.of(extractStage(temp), cleanStage, chunkStage));
        long doc = upload("rag-sample-regulations.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        return doc;
    }

    List<Map<String, Object>> chunkRows(long fileId) {
        return db.queryForList("SELECT * FROM academic_document_chunks WHERE file_id=? ORDER BY chunk_index", fileId);
    }

    @Test
    void sampleDocumentGivesSixChunksWithoutVectors() throws Exception {
        long doc = sample();
        long file = currentFile(doc);
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("failed_stage", "EMBED")
                .containsEntry("last_error_code", "INGESTION_STAGE_NOT_AVAILABLE");
        var rows = chunkRows(file);
        assertThat(rows).extracting(r -> r.get("chunk_index")).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(rows).extracting(r -> r.get("char_count")).containsExactly(425, 631, 306, 315, 273, 208);
        assertThat(rows).allSatisfy(r -> assertThat(r).containsEntry("embedding", null).containsEntry("embedding_model", null)
                .containsEntry("embedded_at", null).containsEntry("page_start", 1).containsEntry("page_end", 1));
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("chunkCount").value(6))
                .andExpect(jsonPath("embeddedChunkCount").value(0)).andExpect(jsonPath("failedStage").value("EMBED"));

        // FULL replaces the chunks (same content, new rows); RESUME after the EMBED failure leaves them untouched.
        var ids = db.queryForList("SELECT id FROM academic_document_chunks WHERE file_id=? ORDER BY chunk_index", Long.class, file);
        var contents = db.queryForList("SELECT content FROM academic_document_chunks WHERE file_id=? ORDER BY chunk_index", String.class, file);
        reprocess(doc, "{\"mode\":\"FULL\"}").andExpect(status().isAccepted());
        switchable.runOnce();
        var fullIds = db.queryForList("SELECT id FROM academic_document_chunks WHERE file_id=? ORDER BY chunk_index", Long.class, file);
        assertThat(fullIds).hasSize(6).doesNotContainAnyElementsOf(ids);
        assertThat(db.queryForList("SELECT content FROM academic_document_chunks WHERE file_id=? ORDER BY chunk_index", String.class, file))
                .isEqualTo(contents);
        reprocess(doc, null).andExpect(status().isAccepted());
        switchable.runOnce();
        assertThat(db.queryForList("SELECT id FROM academic_document_chunks WHERE file_id=? ORDER BY chunk_index", Long.class, file))
                .isEqualTo(fullIds);
        assertThat(processingRow(file)).containsEntry("failed_stage", "EMBED");
    }

    @Test
    void pagesWithoutCleanedTextFailChunking() throws Exception {
        var worker = worker(List.of(extractStage(temp), fake(com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.IngestionStageType.CLEAN), chunkStage));
        long doc = upload("a.txt", TXT, KnowledgeRealFiles.utf8("Article 1. Text."));
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(processingRow(currentFile(doc))).containsEntry("failed_stage", "CHUNK")
                .containsEntry("last_error_code", "DOCUMENT_CHUNKING_FAILED").containsEntry("next_attempt_at", null);
    }

    @Test
    void savingAChunkEntityNeverWritesTheEmbeddingColumns() throws Exception {
        long doc = upload("a.txt", TXT, KnowledgeRealFiles.utf8("x"));
        long fileId = currentFile(doc);
        var chunk = new AcademicDocumentChunk();
        chunk.setFile(entityManager.getReference(AcademicDocumentFile.class, fileId));
        chunk.setChunkIndex(0);
        chunk.setContent("c");
        chunk.setContentHash("f".repeat(64));
        chunk.setCharCount(1);
        chunk.setTokenEstimate(1);
        chunk.setPageStart(1);
        chunk.setPageEnd(1);
        chunk.setEmbeddingModel("model-x");
        chunk.setEmbeddedAt(LocalDateTime.now());
        chunk.setCreatedAt(LocalDateTime.now());
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s -> chunkRepository.saveAndFlush(chunk));
        assertThat(chunkRows(fileId)).singleElement().satisfies(r ->
                assertThat(r).containsEntry("embedding_model", null).containsEntry("embedded_at", null));
    }

    @Test
    void chunkEndpoints() throws Exception {
        long doc = sample();
        long file = currentFile(doc);
        perform(get(BASE + "/" + doc + "/chunks"), null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("fileId").value(file)).andExpect(jsonPath("totalElements").value(6))
                .andExpect(jsonPath("content[4].chunkIndex").value(4)).andExpect(jsonPath("content[4].sectionTitle").value("Chapter 5 Graduation"))
                .andExpect(jsonPath("content[4].charCount").value(273)).andExpect(jsonPath("content[4].embedded").value(false))
                .andExpect(jsonPath("content[4].preview").value(org.hamcrest.Matchers.startsWith("Chapter 5 Graduation\nArticle 11.")))
                .andExpect(jsonPath("content[0].content").doesNotExist()).andExpect(jsonPath("content[0].embedding").doesNotExist());
        perform(get(BASE + "/" + doc + "/chunks").param("page", "2").param("size", "2"), null)
                .andExpect(jsonPath("content[0].chunkIndex").value(4)).andExpect(jsonPath("last").value(true));
        var detail = perform(get(BASE + "/" + doc + "/chunks/4"), null).andExpect(status().isOk())
                .andExpect(jsonPath("sectionTitle").value("Chapter 5 Graduation")).andExpect(jsonPath("pageStart").value(1))
                .andExpect(jsonPath("embedded").value(false)).andExpect(jsonPath("embeddingModel").doesNotExist())
                .andExpect(jsonPath("contentHash").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{64}")))
                .andExpect(jsonPath("content").value(org.hamcrest.Matchers.containsString("at least 120 credits")))
                .andExpect(jsonPath("embedding").doesNotExist()).andReturn();
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("vector");
        perform(get(BASE + "/" + doc + "/chunks/0"), null).andExpect(status().isOk()).andExpect(jsonPath("chunkIndex").value(0));
        perform(get(BASE + "/" + doc + "/chunks/5"), null).andExpect(jsonPath("sectionTitle").value("หมวด 6 การลาพักการศึกษา"))
                .andExpect(jsonPath("content").value(org.hamcrest.Matchers.containsString("ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษาปกติติดต่อกัน")));
        perform(get(BASE + "/" + doc + "/chunks/9999"), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_CHUNK_NOT_FOUND"));
        for (var bad : List.of(get(BASE + "/" + doc + "/chunks/-1"), get(BASE + "/" + doc + "/chunks/x"),
                get(BASE + "/" + doc + "/chunks").param("size", "101"), get(BASE + "/" + doc + "/chunks").param("q", "1")))
            perform(bad, null).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        perform(get(BASE + "/999999/chunks"), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_NOT_FOUND"));
        long noFile = document("No file", categoryId, "2026-2027", null, null, null);
        perform(get(BASE + "/" + noFile + "/chunks/0"), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_FILE_NOT_FOUND"));

        mvc.perform(get(BASE + "/" + doc + "/chunks")).andExpect(status().isUnauthorized());
        String student = studentToken("RAG-CHUNK-1");
        mvc.perform(get(BASE + "/" + doc + "/chunks/0").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            access = login();
            perform(get(BASE + "/" + doc + "/chunks"), null).andExpect(status().isOk());
            perform(get(BASE + "/" + doc + "/chunks/0"), null).andExpect(status().isOk());
        }
    }
}
