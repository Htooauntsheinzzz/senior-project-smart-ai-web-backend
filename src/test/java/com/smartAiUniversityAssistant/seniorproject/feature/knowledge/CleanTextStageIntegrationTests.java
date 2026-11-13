package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Spec file 48, section 16.4: the real EXTRACT and CLEAN stages only. */
class CleanTextStageIntegrationTests extends IngestionTestSupport {
    @TempDir Path temp;

    long process(String filename, String contentType, byte[] content) throws Exception {
        var worker = worker(List.of(extractStage(temp), cleanStage));
        long doc = upload(filename, contentType, content);
        reprocess(doc, null).andExpect(status().isAccepted());
        worker.runOnce();
        return doc;
    }

    List<Map<String, Object>> pageRows(long fileId) {
        return db.queryForList("SELECT * FROM academic_document_pages WHERE file_id=? ORDER BY page_number", fileId);
    }

    @Test
    void sampleDocumentIsCleanedAndRawTextIsUnchanged() throws Exception {
        String sample = Files.readString(Path.of("docker/dev-fixtures/rag-sample-regulations.txt"));
        long doc = process("rag-sample-regulations.txt", TXT, KnowledgeRealFiles.utf8(sample));
        long file = currentFile(doc);
        assertThat(processingRow(file)).containsEntry("status", "FAILED").containsEntry("failed_stage", "CHUNK")
                .containsEntry("last_error_code", "INGESTION_STAGE_NOT_AVAILABLE");
        var row = pageRows(file).get(0);
        assertThat(row.get("raw_text")).isEqualTo(ExtractTextStage.sanitize(sample.stripTrailing()));
        String cleaned = (String) row.get("cleaned_text");
        assertThat(row.get("cleaned_char_count")).isEqualTo(cleaned.length());
        assertThat(cleaned).doesNotContain("\n\n\n").contains("at least 120 credits")
                .contains("ข้อ 13 นักศึกษาสามารถลาพักการศึกษาได้ครั้งละไม่เกิน 2 ภาคการศึกษาปกติติดต่อกัน");
        perform(get(BASE + "/" + doc + "/pages/1"), null).andExpect(jsonPath("cleanedText").value(cleaned))
                .andExpect(jsonPath("rawText").value((String) row.get("raw_text")));
        perform(get(BASE + "/" + doc + "/pages"), null).andExpect(jsonPath("content[0].preview").value(cleaned.substring(0, 200)))
                .andExpect(jsonPath("content[0].cleanedCharCount").value(cleaned.length()));

        // A second run gives identical cleaned text.
        reprocess(doc, "{\"mode\":\"FULL\"}").andExpect(status().isAccepted());
        switchable.runOnce();
        assertThat(pageRows(file).get(0).get("cleaned_text")).isEqualTo(cleaned);
    }

    @Test
    void pdfWithRepeatedHeaderPageNumbersAndWrappedParagraphs() throws Exception {
        var topics = List.of("registration", "tuition", "examination", "graduation", "leave");
        String[] pages = new String[5];
        var bodies = new ArrayList<String>();
        for (int p = 1; p <= 5; p++) {
            String t = topics.get(p - 1);
            var lines = List.of("The " + t + " office accepts every request form until the end of",
                    "the second week, and the " + t + " committee answers each request",
                    "within ten working days after the " + t + " office received it",
                    "from the " + t + " applicant.");
            pages[p - 1] = "Sample University Handbook 2026\n" + String.join("\n", lines) + "\n" + p;
            bodies.add(String.join(" ", lines));
        }
        long doc = process("handbook.pdf", PDF, KnowledgeRealFiles.pdf(pages));
        var rows = pageRows(currentFile(doc));
        assertThat(rows).hasSize(5);
        assertThat(rows.get(0).get("cleaned_text")).isEqualTo("Sample University Handbook 2026\n" + bodies.get(0));
        for (int p = 2; p <= 5; p++) assertThat(rows.get(p - 1).get("cleaned_text")).isEqualTo(bodies.get(p - 1));
        assertThat((String) rows.get(1).get("raw_text")).contains("Sample University Handbook 2026");
    }

    @Test
    void thaiDigitsAreConvertedOnlyInCleanedText() throws Exception {
        long doc = process("thai.txt", TXT, KnowledgeRealFiles.utf8("ต้องสอบได้ไม่น้อยกว่า ๑๒๐ หน่วยกิต"));
        var row = pageRows(currentFile(doc)).get(0);
        assertThat((String) row.get("raw_text")).contains("๑๒๐");
        assertThat((String) row.get("cleaned_text")).isEqualTo("ต้องสอบได้ไม่น้อยกว่า 120 หน่วยกิต");
    }

    @Test
    void onlyPageNumbersIsEmptyAndMostlyUndecodableIsUnreadable() throws Exception {
        long numbers = process("numbers.pdf", PDF, KnowledgeRealFiles.pdf("1", "2", "Page 3"));
        assertThat(processingRow(currentFile(numbers))).containsEntry("failed_stage", "CLEAN")
                .containsEntry("last_error_code", "DOCUMENT_TEXT_EMPTY").containsEntry("next_attempt_at", null);
        long garbled = process("garbled.txt", TXT, KnowledgeRealFiles.utf8("Rules " + "�".repeat(40) + " end"));
        var row = processingRow(currentFile(garbled));
        assertThat(row).containsEntry("failed_stage", "CLEAN").containsEntry("last_error_code", "DOCUMENT_TEXT_UNREADABLE");
        assertThat((String) row.get("last_error_message")).doesNotContain("Rules");
    }
}
