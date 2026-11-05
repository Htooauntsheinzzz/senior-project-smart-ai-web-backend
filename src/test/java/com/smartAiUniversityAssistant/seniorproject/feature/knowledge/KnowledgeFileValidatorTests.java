package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeStorageProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.KnowledgeFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.KnowledgeFileValidatorImpl;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;

class KnowledgeFileValidatorTests {
    private final KnowledgeFileValidatorImpl validator = new KnowledgeFileValidatorImpl(
            new KnowledgeStorageProperties(true, 1, new KnowledgeStorageProperties.Storage("local", "/unused", false)));

    @Test
    void acceptsTheFourSupportedFormatsWithCanonicalTypes() throws Exception {
        assertThat(validator.validate(file("handbook.pdf", PDF, pdf("x")))).extracting("extension", "mimeType").containsExactly("pdf", PDF);
        assertThat(validator.validate(file("Handbook.DOCX", DOCX, docx()))).extracting("extension", "mimeType").containsExactly("docx", DOCX);
        assertThat(validator.validate(file("calendar.xlsx", XLSX, xlsx()))).extracting("extension").isEqualTo("xlsx");
        assertThat(validator.validate(file("notes.txt", "text/plain; charset=UTF-8",
                "\uFEFFกฎระเบียบ — rules\nLine 2".getBytes(StandardCharsets.UTF_8)))).extracting("mimeType").isEqualTo(TXT);
    }

    @Test
    void rejectsMissingEmptyAndOversizedFiles() {
        assertCode(null, "DOCUMENT_FILE_REQUIRED");
        assertCode(file("a.pdf", PDF, new byte[0]), "DOCUMENT_FILE_EMPTY");
        assertCode(file("a.txt", TXT, new byte[1024 * 1024 + 1]), "DOCUMENT_FILE_TOO_LARGE");
    }

    @Test
    void rejectsUnsupportedExtensionsAndSpoofedMediaTypes() throws Exception {
        for (var name : List.of("tool.exe", "page.html", "macro.docm", "archive.zip", "script.js", "noextension", ".pdf", "a.pdf.exe"))
            assertCode(file(name, "application/octet-stream", pdf("x")), "DOCUMENT_FILE_TYPE_UNSUPPORTED");
        assertCode(file("handbook.pdf", TXT, pdf("x")), "DOCUMENT_FILE_TYPE_UNSUPPORTED");
        assertCode(file("handbook.pdf", null, pdf("x")), "DOCUMENT_FILE_TYPE_UNSUPPORTED");
        assertCode(file("handbook.docx", PDF, docx()), "DOCUMENT_FILE_TYPE_UNSUPPORTED");
    }

    @Test
    void rejectsContentThatDoesNotMatchTheType() throws Exception {
        assertCode(file("fake.pdf", PDF, "plain text".getBytes(StandardCharsets.UTF_8)), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("truncated.pdf", PDF, "%PDF-1.4 no trailer".getBytes(StandardCharsets.UTF_8)), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("fake.docx", DOCX, pdf("x")), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("sheet.docx", DOCX, xlsx()), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("binary.txt", TXT, new byte[] {'a', 0, 'b'}), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("latin1.txt", TXT, new byte[] {'c', 'a', 'f', (byte) 0xE9}), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("page.txt", TXT, "  <!DOCTYPE html><script>x</script>".getBytes(StandardCharsets.UTF_8)), "DOCUMENT_FILE_CONTENT_INVALID");
    }

    @Test
    void rejectsMacrosEmbeddedCodeTraversalAndDecompressionBombs() throws Exception {
        var types = contentTypes("wordprocessingml.document.main+xml");
        assertCode(file("m.docx", DOCX, zip(Map.of("[Content_Types].xml", types, "word/document.xml", "<w/>",
                "word/vbaProject.bin", "x"))), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("m.docx", DOCX, zip(Map.of("[Content_Types].xml", contentTypes("word.document.macroEnabled.main+xml"),
                "word/document.xml", "<w/>"))), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("o.docx", DOCX, zip(Map.of("[Content_Types].xml", types, "word/document.xml", "<w/>",
                "word/embeddings/oleObject1.bin", "x"))), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("e.docx", DOCX, zip(Map.of("[Content_Types].xml", types, "word/document.xml", "<w/>",
                "word/media/run.exe", "MZ"))), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("t.docx", DOCX, zip(Map.of("[Content_Types].xml", types, "word/document.xml", "<w/>",
                "../../evil.xml", "x"))), "DOCUMENT_FILE_CONTENT_INVALID");
        assertCode(file("bomb.docx", DOCX, zip(Map.of("[Content_Types].xml", types, "word/document.xml", "<w/>",
                "word/padding.xml", "0".repeat(2_000_000)))), "DOCUMENT_FILE_CONTENT_INVALID");
        assertThat(validator.validate(file("printer.docx", DOCX, zip(Map.of("[Content_Types].xml", types,
                "word/document.xml", "<w/>", "word/printerSettings/printerSettings1.bin", "settings"))))).isNotNull();
    }

    @Test
    void sanitizesDisplayFilenames() throws Exception {
        assertThat(validator.validate(file("../../etc/handbook.pdf", PDF, pdf("x"))).filename()).isEqualTo("handbook.pdf");
        assertThat(validator.validate(file("C:\\Users\\x\\My  \"Hand\"\u202Ebook.pdf", PDF, pdf("x"))).filename()).isEqualTo("My Handbook.pdf");
        assertThat(validator.validate(file("a".repeat(300) + ".pdf", PDF, pdf("x"))).filename()).hasSize(255).endsWith(".pdf");
    }

    private void assertCode(MockMultipartFile file, String code) {
        assertThatThrownBy(() -> validator.validate(file)).isInstanceOfSatisfying(KnowledgeFailure.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }
}
