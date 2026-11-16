package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** With KNOWLEDGE_INGESTION_ENABLED=false nothing is processed and reprocess answers 503 (spec file 46, section 20.3). */
@TestPropertySource(properties = "app.knowledge.ingestion.enabled=false")
class KnowledgeIngestionDisabledIntegrationTests extends KnowledgeIntegrationSupport {
    private static final String BASE = "/api/v1/admin/knowledge/documents";

    @Test
    void ingestionIsNotAvailable() throws Exception {
        long category = category("REGULATION", "Regulations");
        var builder = multipart(BASE + "/upload").file(KnowledgeTestFiles.file("a.txt", KnowledgeTestFiles.TXT,
                "Article 1.".getBytes(StandardCharsets.UTF_8)));
        builder.param("title", "Sample").param("categoryId", String.valueOf(category)).param("academicYearLabel", "2026-2027");
        builder.header("Authorization", "Bearer " + access);
        long doc = tree(mvc.perform(builder).andExpect(status().isCreated())
                .andExpect(jsonPath("processingStatus").value("UPLOADED")).andReturn()).get("documentId").asLong();
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(status().isOk())
                .andExpect(jsonPath("status").value("UPLOADED")).andExpect(jsonPath("ingestionAvailable").value(false))
                .andExpect(jsonPath("message").value("Awaiting AI processing. Document ingestion is not available yet."));
        perform(post(BASE + "/" + doc + "/reprocess"), null).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("code").value("DOCUMENT_PROCESSING_NOT_AVAILABLE"))
                .andExpect(jsonPath("message").value("Document ingestion is not available."));
        assertThat(db.queryForObject("SELECT status FROM academic_document_processing p JOIN academic_document_files f ON f.id=p.file_id WHERE f.document_id=?",
                String.class, doc)).isEqualTo("UPLOADED");
    }
}
