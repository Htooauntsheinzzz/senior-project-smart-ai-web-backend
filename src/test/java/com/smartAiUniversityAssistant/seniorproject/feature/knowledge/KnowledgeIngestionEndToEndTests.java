package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.IngestionStage;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spec file 51, section 15.3: the real application (worker and all five stage beans); only the EmbeddingClient bean is
 * the deterministic test client.
 */
class KnowledgeIngestionEndToEndTests extends IngestionTestSupport {
    @Autowired List<IngestionStage> applicationStages;

    @Test
    void everyFileTypeBecomesReady() throws Exception {
        var docs = new LinkedHashMap<String, Long>();
        docs.put("pdf", upload("a.pdf", PDF, KnowledgeRealFiles.pdf("Chapter 1 Rules\nArticle 1. Students register online every semester before week two.",
                "Article 2. Tuition is paid within fourteen days after the first day of the semester.")));
        docs.put("docx", upload("a.docx", DOCX, new KnowledgeRealFiles.Docx().heading("Heading1", "Chapter 1 Rules")
                .paragraph("Students register online every semester before the second week begins.").bytes()));
        docs.put("xlsx", upload("a.xlsx", XLSX, KnowledgeRealFiles.xlsx()));
        docs.put("txt", upload("a.txt", TXT, KnowledgeRealFiles.utf8(sampleText())));
        for (long doc : docs.values())
            perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("status").value("UPLOADED"))
                    .andExpect(jsonPath("ingestionAvailable").value(true))
                    .andExpect(jsonPath("message").value("Uploaded. Waiting to be queued for AI processing."));
        for (int i = 0; i < docs.size(); i++) switchable.runOnce();
        for (var entry : docs.entrySet())
            perform(get(BASE + "/" + entry.getValue() + "/processing"), null).andExpect(status().isOk())
                    .andExpect(jsonPath("status").value("READY")).andExpect(jsonPath("ingestionAvailable").value(true))
                    .andExpect(jsonPath("indexStale").value(false)).andExpect(jsonPath("pageCount").value(org.hamcrest.Matchers.greaterThan(0)))
                    .andExpect(jsonPath("chunkCount").value(org.hamcrest.Matchers.greaterThan(0)))
                    .andExpect(jsonPath("message").value("Indexed and ready for AI retrieval."));
        perform(get(BASE + "/" + docs.get("txt") + "/processing"), null).andExpect(jsonPath("chunkCount").value(6))
                .andExpect(jsonPath("embeddedChunkCount").value(6));
    }

    @Test
    void archivedDocumentsAndAutoQueueOff() throws Exception {
        long doc = upload("a.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        perform(patch(BASE + "/" + doc + "/archive"), null).andExpect(status().isOk());
        switchable.runOnce();
        assertThat(statusOf(currentFile(doc))).isEqualTo("UPLOADED");
        perform(patch(BASE + "/" + doc + "/activate"), null).andExpect(status().isOk());
        switchable.runOnce();
        assertThat(statusOf(currentFile(doc))).isEqualTo("READY");

        var manual = worker(properties(true, false, 4, Duration.ofMinutes(30)), applicationStages);
        long other = upload("b.txt", TXT, KnowledgeRealFiles.utf8(chapters(2, "")));
        manual.runOnce();
        assertThat(statusOf(currentFile(other))).isEqualTo("UPLOADED");
        reprocess(other, null).andExpect(status().isAccepted());
        manual.runOnce();
        assertThat(statusOf(currentFile(other))).isEqualTo("READY");
    }

    @Test
    void deletingAReadyDocumentRemovesItsPagesAndChunksOnly() throws Exception {
        long doc = upload("a.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        switchable.runOnce();
        long file = currentFile(doc);
        var stored = storedPath(file);
        assertThat(chunks(file)).isEqualTo(6);
        perform(delete(BASE + "/" + doc), null).andExpect(status().isNoContent());
        switchable.runOnce();
        assertThat(chunks(file) + pages(file)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM academic_document_files WHERE id=?", Long.class, file)).isOne();
        assertThat(Files.exists(stored)).isTrue();
    }

    @Test
    void reprocessOfAReadyDocumentAndModelChange() throws Exception {
        long doc = upload("a.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        switchable.runOnce();
        long file = currentFile(doc);
        reprocess(doc, null).andExpect(status().isConflict()).andExpect(jsonPath("code").value("DOCUMENT_ALREADY_PROCESSED"));
        reprocess(doc, "{\"mode\":\"FULL\"}").andExpect(status().isAccepted());
        switchable.runOnce();
        assertThat(statusOf(file)).isEqualTo("READY");

        var ids = db.queryForList("SELECT id FROM academic_document_chunks WHERE file_id=? ORDER BY id", Long.class, file);
        beanClient.model("test-embedding-B");
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("status").value("READY"))
                .andExpect(jsonPath("indexStale").value(true)).andExpect(jsonPath("embeddedChunkCount").value(0))
                .andExpect(jsonPath("message").value("Indexed with a different embedding model. Reprocess the document to make it searchable again."));
        switchable.runOnce();
        assertThat(processingRow(file)).containsEntry("status", "READY");   // stale files are never queued automatically
        reprocess(doc, null).andExpect(status().isAccepted()).andExpect(jsonPath("failedStage").value("EMBED"));
        switchable.runOnce();
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("status").value("READY"))
                .andExpect(jsonPath("indexStale").value(false)).andExpect(jsonPath("embeddedChunkCount").value(6));
        assertThat(db.queryForList("SELECT id FROM academic_document_chunks WHERE file_id=? ORDER BY id", Long.class, file)).isEqualTo(ids);
        assertThat(db.queryForList("SELECT DISTINCT embedding_model FROM academic_document_chunks WHERE file_id=?", String.class, file))
                .containsExactly("test-embedding-B");
    }

    @Test
    void indexStatisticsEndpoint() throws Exception {
        long doc = upload("a.txt", TXT, KnowledgeRealFiles.utf8(sampleText()));
        switchable.runOnce();
        perform(get("/api/v1/admin/knowledge/index/statistics"), null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("ingestionAvailable").value(true)).andExpect(jsonPath("embeddingConfigured").value(true))
                .andExpect(jsonPath("embeddingModel").value(DeterministicEmbeddingClient.MODEL)).andExpect(jsonPath("dimensions").value(768))
                .andExpect(jsonPath("readyDocuments").value(1)).andExpect(jsonPath("staleDocuments").value(0))
                .andExpect(jsonPath("searchableDocuments").value(1)).andExpect(jsonPath("totalChunks").value(6))
                .andExpect(jsonPath("searchableChunks").value(6)).andExpect(jsonPath("chunksOfOtherModels").value(0))
                .andExpect(jsonPath("outdatedChunks").value(0)).andExpect(jsonPath("totalPages").value(1))
                .andExpect(jsonPath("apiKey").doesNotExist());
        mvc.perform(get("/api/v1/admin/knowledge/index/statistics")).andExpect(status().isUnauthorized());
        String student = studentToken("RAG-IDX-1");
        mvc.perform(get("/api/v1/admin/knowledge/index/statistics").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            access = login();
            perform(get("/api/v1/admin/knowledge/index/statistics"), null).andExpect(status().isOk());
        }
    }
}
