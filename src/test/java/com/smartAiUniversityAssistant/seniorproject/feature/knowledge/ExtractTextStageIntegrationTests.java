package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.AcademicDocumentPageRepository;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage;
import jakarta.persistence.EntityManager;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spec file 47, sections 20.3 and 20.4: the real EXTRACT stage only, and the page inspection endpoints. */
class ExtractTextStageIntegrationTests extends IngestionTestSupport {
    @TempDir Path temp;

    ExtractTextStage stage(Duration timeout, List<DocumentTextExtractor> using) { return extractStage(temp, timeout, using); }

    ExtractTextStage stage() { return stage(Duration.ofMinutes(5), extractors); }

    long process(String filename, String contentType, byte[] content) throws Exception {
        var worker = worker(List.of(stage()));
        long doc = upload(filename, contentType, content);
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        return doc;
    }

    List<Map<String, Object>> pageRows(long fileId) {
        return db.queryForList("SELECT * FROM academic_document_pages WHERE file_id=? ORDER BY page_number", fileId);
    }

    @Test
    void txtIsExtractedAndTheJobStopsAtClean() throws Exception {
        String sample = Files.readString(Path.of("docker/dev-fixtures/rag-sample-regulations.txt"));
        long doc = process("rag-sample-regulations.txt", TXT, KnowledgeRealFiles.utf8(sample));
        long file = currentFile(doc);
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("failed_stage", "CLEAN")
                .containsEntry("last_error_code", "INGESTION_STAGE_NOT_AVAILABLE");
        var rows = pageRows(file);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("page_number", 1).containsEntry("unit_type", "TEXT").containsEntry("unit_label", null)
                    .containsEntry("cleaned_text", null).containsEntry("cleaned_char_count", null);
            assertThat((String) row.get("raw_text")).contains("at least 120 credits")
                    .contains("ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษาปกติติดต่อกัน");
            assertThat(row.get("raw_char_count")).isEqualTo(((String) row.get("raw_text")).length());
        });
        perform(get(BASE + "/" + doc + "/processing"), null).andExpect(jsonPath("pageCount").value(1))
                .andExpect(jsonPath("chunkCount").value(0)).andExpect(jsonPath("ingestionAvailable").value(false));
        assertThat(temp).isEmptyDirectory();
    }

    @Test
    void pdfDocxAndXlsxAreExtracted() throws Exception {
        long pdf = process("a.pdf", PDF, KnowledgeRealFiles.pdf("Page one text", "", "Page three text"));
        assertThat(pageRows(currentFile(pdf))).extracting(r -> r.get("unit_type")).containsExactly("PAGE", "PAGE", "PAGE");
        assertThat((String) pageRows(currentFile(pdf)).get(2).get("raw_text")).contains("Page three text");
        long docx = process("a.docx", DOCX, new KnowledgeRealFiles.Docx().heading("Heading1", "Chapter 1 Rules").paragraph("Body").bytes());
        assertThat(pageRows(currentFile(docx))).singleElement().satisfies(r ->
                assertThat(r).containsEntry("unit_type", "SECTION").containsEntry("unit_label", "Chapter 1 Rules"));
        long xlsx = process("a.xlsx", XLSX, KnowledgeRealFiles.xlsx());
        assertThat(pageRows(currentFile(xlsx))).extracting(r -> r.get("unit_label")).containsExactly("Courses", "Calendar");
        for (long doc : List.of(pdf, docx, xlsx))
            assertThat(processingRow(currentFile(doc))).containsEntry("failed_stage", "CLEAN");
        assertThat(temp).isEmptyDirectory();
    }

    @Test
    void fullReprocessReplacesTheRows() throws Exception {
        long doc = process("a.pdf", PDF, KnowledgeRealFiles.pdf("one", "two"));
        long file = currentFile(doc);
        var before = db.queryForList("SELECT id FROM academic_document_pages WHERE file_id=?", Long.class, file);
        reprocess(doc, "{\"mode\":\"FULL\"}").andExpect(status().isAccepted());
        switchable.runOnce();
        var after = db.queryForList("SELECT id FROM academic_document_pages WHERE file_id=?", Long.class, file);
        assertThat(after).hasSize(2).doesNotContainAnyElementsOf(before);
    }

    @Test
    void changedOrMissingStoredFiles() throws Exception {
        var worker = worker(List.of(stage()));
        long changed = upload("secret-name.txt", TXT, KnowledgeRealFiles.utf8("Original confidential text"));
        Files.writeString(storedPath(currentFile(changed)), "Tampered confidential text");
        reprocess(changed, null).andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(processingRow(currentFile(changed))).containsEntry("last_error_code", "DOCUMENT_CHECKSUM_MISMATCH")
                .containsEntry("failed_stage", "EXTRACT").containsEntry("next_attempt_at", null);
        assertThat(pages(currentFile(changed))).isZero();

        long missing = upload("b.txt", TXT, KnowledgeRealFiles.utf8("text"));
        Files.delete(storedPath(currentFile(missing)));
        reprocess(missing, null).andExpect(status().isAccepted());
        worker.runOnce();
        assertThat(processingRow(currentFile(missing))).containsEntry("last_error_code", "DOCUMENT_FILE_MISSING")
                .containsEntry("next_attempt_at", null);
        assertThat(temp).isEmptyDirectory();
    }

    @Test
    void damagedEncryptedAndScannedFiles() throws Exception {
        long damaged = process("confidential-name.pdf", PDF, KnowledgeTestFiles.pdf("Confidential words"));
        var row = processingRow(currentFile(damaged));
        assertThat(row).containsEntry("status", "FAILED").containsEntry("failed_stage", "EXTRACT")
                .containsEntry("last_error_code", "DOCUMENT_TEXT_EXTRACTION_FAILED").containsEntry("next_attempt_at", null);
        assertThat((String) row.get("last_error_message")).doesNotContain("Confidential").doesNotContain("confidential-name");
        long encrypted = process("e.pdf", PDF, KnowledgeRealFiles.passwordPdf("Secret words"));
        assertThat(processingRow(currentFile(encrypted))).containsEntry("last_error_code", "DOCUMENT_ENCRYPTED");
        long scanned = process("s.pdf", PDF, KnowledgeRealFiles.scannedPdf(2));
        assertThat(processingRow(currentFile(scanned))).containsEntry("last_error_code", "DOCUMENT_TEXT_EMPTY")
                .containsEntry("failed_stage", "EXTRACT");
        assertThat(pages(currentFile(scanned))).isZero();
        assertThat(temp).isEmptyDirectory();
    }

    @Test
    void slowExtractorTimesOutAndTheTemporaryFileIsRemoved() throws Exception {
        DocumentTextExtractor slow = new DocumentTextExtractor() {
            public boolean supports(String extension) { return "txt".equals(extension); }
            public List<ExtractedUnit> extract(Path file, ExtractionLimits limits) {
                assertThat(file).exists().hasParent(temp);
                try { Thread.sleep(30_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return List.of(new ExtractedUnit(DocumentUnitType.TEXT, null, "late"));
            }
        };
        var using = new ArrayList<>(extractors.stream().filter(e -> !e.supports("txt")).toList());
        using.add(slow);
        var worker = worker(List.of(stage(Duration.ofMillis(200), using)));
        long doc = upload("a.txt", TXT, KnowledgeRealFiles.utf8("text"));
        reprocess(doc, null).andExpect(status().isAccepted());
        long started = System.nanoTime();
        worker.runOnce();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
        assertThat(processingRow(currentFile(doc))).containsEntry("last_error_code", "DOCUMENT_EXTRACTION_TIMEOUT")
                .containsEntry("failed_stage", "EXTRACT").containsEntry("next_attempt_at", null);
        assertThat(temp).isEmptyDirectory();
    }

    // ---------- endpoints (section 20.4) ----------

    @Test
    void pageEndpointsReturnStoredValues() throws Exception {
        long doc = process("a.pdf", PDF, KnowledgeRealFiles.pdf("Alpha page", "Beta page", "Gamma page"));
        long file = currentFile(doc);
        perform(get(BASE + "/" + doc + "/pages"), null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("documentId").value(doc)).andExpect(jsonPath("fileId").value(file))
                .andExpect(jsonPath("revisionNumber").value(1)).andExpect(jsonPath("totalElements").value(3))
                .andExpect(jsonPath("content[0].pageNumber").value(1)).andExpect(jsonPath("content[0].unitType").value("PAGE"))
                .andExpect(jsonPath("content[0].cleanedCharCount").doesNotExist())
                .andExpect(jsonPath("content[1].preview").value(org.hamcrest.Matchers.containsString("Beta page")))
                .andExpect(jsonPath("content[0].rawText").doesNotExist()).andExpect(jsonPath("first").value(true))
                .andExpect(jsonPath("last").value(true));
        perform(get(BASE + "/" + doc + "/pages").param("page", "1").param("size", "1"), null).andExpect(status().isOk())
                .andExpect(jsonPath("content.length()").value(1)).andExpect(jsonPath("content[0].pageNumber").value(2))
                .andExpect(jsonPath("totalPages").value(3)).andExpect(jsonPath("first").value(false)).andExpect(jsonPath("last").value(false));
        perform(get(BASE + "/" + doc + "/pages/3"), null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("pageNumber").value(3)).andExpect(jsonPath("rawText").value(org.hamcrest.Matchers.containsString("Gamma page")))
                .andExpect(jsonPath("cleanedText").doesNotExist()).andExpect(jsonPath("rawCharCount").isNumber())
                .andExpect(jsonPath("storageKey").doesNotExist());
        for (var bad : List.of(get(BASE + "/" + doc + "/pages").param("size", "101"), get(BASE + "/" + doc + "/pages").param("sort", "id,asc"),
                get(BASE + "/" + doc + "/pages").param("page", "-1"), get(BASE + "/" + doc + "/pages").param("size", "1").param("size", "2"),
                get(BASE + "/" + doc + "/pages/0"), get(BASE + "/" + doc + "/pages/abc"), get(BASE + "/0/pages")))
            perform(bad, null).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
        perform(get(BASE + "/" + doc + "/pages/9999"), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("DOCUMENT_PAGE_NOT_FOUND"));
        perform(get(BASE + "/999999/pages"), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_NOT_FOUND"));
        long noFile = document("No file", categoryId, "2026-2027", null, null, null);
        perform(get(BASE + "/" + noFile + "/pages"), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("DOCUMENT_FILE_NOT_FOUND"));

        perform(patch(BASE + "/" + doc + "/archive"), null).andExpect(status().isOk());
        perform(get(BASE + "/" + doc + "/pages/1"), null).andExpect(status().isOk());
        perform(patch(BASE + "/" + doc + "/activate"), null).andExpect(status().isOk());
        replaceFile(doc, "b.pdf", PDF, KnowledgeRealFiles.pdf("New revision")).andExpect(status().isCreated());
        perform(get(BASE + "/" + doc + "/pages"), null).andExpect(status().isOk()).andExpect(jsonPath("revisionNumber").value(2))
                .andExpect(jsonPath("totalElements").value(0)).andExpect(jsonPath("content.length()").value(0));
        perform(delete(BASE + "/" + doc), null).andExpect(status().isNoContent());
        perform(get(BASE + "/" + doc + "/pages"), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void pageEndpointsRequireAnAdminRole() throws Exception {
        long doc = process("a.txt", TXT, KnowledgeRealFiles.utf8("Text"));
        mvc.perform(get(BASE + "/" + doc + "/pages")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + doc + "/pages/1")).andExpect(status().isUnauthorized());
        String student = studentToken("RAG-PAGE-1");
        mvc.perform(get(BASE + "/" + doc + "/pages").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + doc + "/pages/1").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "ACADEMIC_ADMIN")) {
            setRole(role);
            access = login();
            perform(get(BASE + "/" + doc + "/pages"), null).andExpect(status().isOk());
            perform(get(BASE + "/" + doc + "/pages/1"), null).andExpect(status().isOk());
        }
    }
}
