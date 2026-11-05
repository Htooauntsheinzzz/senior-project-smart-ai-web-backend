package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.KnowledgeStorageReconciliationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static com.smartAiUniversityAssistant.seniorproject.feature.knowledge.KnowledgeTestFiles.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeUploadIntegrationTests extends KnowledgeIntegrationSupport {
    private static final String BASE = "/api/v1/admin/knowledge/documents";
    @Autowired KnowledgeStorageReconciliationService reconciliation;
    private long handbook;

    @BeforeEach
    void categoryAndEmptyStorage() throws Exception {
        handbook = category("STUDENT_HANDBOOK", "Student Handbook");
        clearStorage();
    }

    @AfterEach
    void cleanStorage() throws Exception {
        db.execute("DROP TRIGGER IF EXISTS knowledge_test_fail ON academic_document_processing");
        db.execute("DROP FUNCTION IF EXISTS knowledge_test_fail()");
        clearStorage();
    }

    @Test
    void pdfUploadStoresFileAndNormalizedMetadataWithUploadedStatus() throws Exception {
        byte[] content = pdf("Student Handbook");
        var result = upload(form("Student Handbook 2024-2025", handbook, "2024–2025"), file("student-handbook.pdf", PDF, content))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("title").value("Student Handbook 2024-2025"))
                .andExpect(jsonPath("categoryId").value(handbook))
                .andExpect(jsonPath("academicYearLabel").value("2024-2025"))
                .andExpect(jsonPath("filename").value("student-handbook.pdf"))
                .andExpect(jsonPath("mimeType").value(PDF))
                .andExpect(jsonPath("revisionNumber").value(1))
                .andExpect(jsonPath("sizeBytes").value(content.length))
                .andExpect(jsonPath("processingStatus").value("UPLOADED"))
                .andExpect(jsonPath("isActive").value(true))
                .andExpect(jsonPath("uploadedBy").value(actorId))
                .andExpect(jsonPath("uploadedAt").value(clock.instant().toString()))
                .andExpect(jsonPath("storageKey").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("knowledge/").doesNotContain(KNOWLEDGE_STORAGE.toString());
        long documentId = tree(result).get("documentId").asLong();
        long fileId = tree(result).get("fileId").asLong();
        var document = db.queryForMap("SELECT * FROM academic_documents WHERE id=?", documentId);
        assertThat(document).containsEntry("created_by", actorId).containsEntry("is_active", true)
                .containsEntry("academic_year_label", "2024-2025").containsEntry("description", "Official document");
        var stored = db.queryForMap("SELECT * FROM academic_document_files WHERE id=?", fileId);
        assertThat(stored).containsEntry("document_id", documentId).containsEntry("revision_number", 1)
                .containsEntry("is_current", true).containsEntry("uploaded_by", actorId)
                .containsEntry("size_bytes", (long) content.length).containsEntry("file_extension", "pdf");
        assertThat(stored.get("sha256_checksum")).isEqualTo(sha256(content));
        Path physical = KNOWLEDGE_STORAGE.resolve((String) stored.get("storage_key"));
        assertThat(Files.readAllBytes(physical)).isEqualTo(content);
        assertThat(physical.normalize()).startsWith(KNOWLEDGE_STORAGE);
        assertThat(db.queryForMap("SELECT status,attempt_count FROM academic_document_processing WHERE file_id=?", fileId))
                .containsEntry("status", "UPLOADED").containsEntry("attempt_count", 0);
        perform(get(BASE).param("page", "0").param("size", "10"), null).andExpect(status().isOk())
                .andExpect(jsonPath("content[0].id").value(documentId))
                .andExpect(jsonPath("content[0].category.name").value("Student Handbook"))
                .andExpect(jsonPath("content[0].file.extension").value("pdf"))
                .andExpect(jsonPath("content[0].uploadedBy.displayName").value("Knowledge Admin"))
                .andExpect(jsonPath("content[0].processingStatus").value("UPLOADED"));
        perform(get(BASE + "/statistics"), null).andExpect(jsonPath("totalDocuments").value(1))
                .andExpect(jsonPath("processingDocuments").value(1)).andExpect(jsonPath("readyDocuments").value(0));
        perform(get(BASE + "/" + documentId + "/processing"), null).andExpect(status().isOk())
                .andExpect(jsonPath("fileId").value(fileId)).andExpect(jsonPath("status").value("UPLOADED"))
                .andExpect(jsonPath("attemptCount").value(0)).andExpect(jsonPath("startedAt").doesNotExist())
                .andExpect(jsonPath("ingestionAvailable").value(false));
        perform(post(BASE + "/" + documentId + "/reprocess"), null).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("code").value("DOCUMENT_PROCESSING_NOT_AVAILABLE"));
        assertThat(db.queryForObject("SELECT status FROM academic_document_processing WHERE file_id=?", String.class, fileId)).isEqualTo("UPLOADED");
    }

    @Test
    void docxTxtAndXlsxUploadsWithScopes() throws Exception {
        long cs = curriculum(programA, "CS-2024", "ACTIVE");
        var curriculumForm = form("CS Curriculum", handbook, "2024-2025");
        curriculumForm.put("curriculumVersionId", String.valueOf(cs));
        long docx = tree(upload(curriculumForm, file("curriculum.docx", DOCX, docx())).andExpect(status().isCreated())
                .andExpect(jsonPath("mimeType").value(DOCX)).andReturn()).get("documentId").asLong();
        var programForm = form("Program rules", handbook, "2024-2025");
        programForm.put("programId", String.valueOf(programB));
        programForm.put("curriculumVersionId", "");
        upload(programForm, file("rules.txt", "text/plain;charset=UTF-8", "ระเบียบ rules".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isCreated()).andExpect(jsonPath("mimeType").value(TXT));
        var departmentForm = form("Calendar", handbook, "2024-2025");
        departmentForm.put("departmentId", String.valueOf(departmentA));
        upload(departmentForm, file("calendar.xlsx", XLSX, xlsx())).andExpect(status().isCreated());
        perform(get(BASE + "/" + docx), null).andExpect(jsonPath("scope.type").value("CURRICULUM"))
                .andExpect(jsonPath("scope.programId").value(programA));
        perform(get(BASE).param("programId", String.valueOf(programB)), null).andExpect(jsonPath("totalElements").value(1));
        perform(get(BASE).param("departmentId", String.valueOf(departmentA)), null).andExpect(jsonPath("totalElements").value(2));
        assertThat(storedFiles()).isEqualTo(3);
    }

    @Test
    void invalidRequestsAreRejectedBeforeAnythingIsStored() throws Exception {
        var noTitle = form("", handbook, "2024-2025");
        upload(noTitle, file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_UPLOAD_VALIDATION_ERROR"));
        var badCategory = form("T", handbook, "2024-2025");
        badCategory.put("categoryId", "abc");
        upload(badCategory, file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_UPLOAD_VALIDATION_ERROR"));
        upload(form("T", handbook, "2024"), file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_UPLOAD_VALIDATION_ERROR"));
        var audit = form("T", handbook, "2024-2025");
        audit.put("uploadedBy", "1");
        upload(audit, file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_UPLOAD_VALIDATION_ERROR"));
        upload(form("T", handbook, "2024-2025"), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_FILE_REQUIRED"));
        upload(form("T", handbook, "2024-2025"), file("a.pdf", PDF, new byte[0])).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_FILE_EMPTY"));
        upload(form("T", handbook, "2024-2025"), file("a.exe", "application/x-msdownload", new byte[] {'M', 'Z'}))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("code").value("DOCUMENT_FILE_TYPE_UNSUPPORTED"));
        upload(form("T", handbook, "2024-2025"), file("a.pdf", "image/png", pdf("x")))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("code").value("DOCUMENT_FILE_TYPE_UNSUPPORTED"));
        upload(form("T", handbook, "2024-2025"), file("a.pdf", PDF, "MZ not a pdf".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("DOCUMENT_FILE_CONTENT_INVALID"));
        long inactive = category("FINANCIAL_AID", "Financial Aid");
        db.update("UPDATE document_categories SET is_active=FALSE WHERE id=?", inactive);
        upload(form("T", inactive, "2024-2025"), file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_CATEGORY_INVALID"));
        upload(form("T", 999_999L, "2024-2025"), file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_CATEGORY_INVALID"));
        var twoScopes = form("T", handbook, "2024-2025");
        twoScopes.put("programId", String.valueOf(programA));
        twoScopes.put("departmentId", String.valueOf(departmentA));
        upload(twoScopes, file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_SCOPE_INVALID"));
        db.update("UPDATE departments SET is_deleted=TRUE WHERE id=?", departmentB);
        var deletedScope = form("T", handbook, "2024-2025");
        deletedScope.put("departmentId", String.valueOf(departmentB));
        upload(deletedScope, file("a.pdf", PDF, pdf("x"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("DOCUMENT_SCOPE_INVALID"));
        mvc.perform(post(BASE + "/upload").header("Authorization", "Bearer " + access)
                .contentType("application/json").content("{}")).andExpect(status().isUnsupportedMediaType());
        assertThat(db.queryForObject("SELECT count(*) FROM academic_documents", Integer.class)).isZero();
        assertThat(storedFiles()).isZero();
    }

    @Test
    void replacementKeepsHistoryAndDownloadsAreScopedToTheirDocument() throws Exception {
        byte[] first = pdf("first");
        byte[] second = pdf("second revision");
        long id = tree(upload(form("Handbook", handbook, "2024-2025"), file("handbook-v1.pdf", PDF, first))
                .andExpect(status().isCreated()).andReturn()).get("documentId").asLong();
        db.update("UPDATE academic_document_processing SET status='READY'");
        clock.advance(Duration.ofMinutes(1));
        var replaced = replace(id, file("handbook-v2.pdf", PDF, second)).andExpect(status().isCreated())
                .andExpect(jsonPath("revisionNumber").value(2)).andExpect(jsonPath("processingStatus").value("UPLOADED")).andReturn();
        long secondId = tree(replaced).get("fileId").asLong();
        var revisions = tree(perform(get(BASE + "/" + id + "/files"), null).andExpect(status().isOk())
                .andExpect(jsonPath("[0].revisionNumber").value(1)).andExpect(jsonPath("[0].isCurrent").value(false))
                .andExpect(jsonPath("[0].processingStatus").value("READY")).andExpect(jsonPath("[0].filename").value("handbook-v1.pdf"))
                .andExpect(jsonPath("[1].revisionNumber").value(2)).andExpect(jsonPath("[1].isCurrent").value(true))
                .andExpect(jsonPath("[1].processingStatus").value("UPLOADED"))
                .andExpect(jsonPath("[1].uploadedBy.displayName").value("Knowledge Admin"))
                .andExpect(jsonPath("[0].storageKey").doesNotExist()).andReturn());
        long firstId = revisions.get(0).get("fileId").asLong();
        assertThat(storedFiles()).isEqualTo(2);
        perform(get(BASE + "/statistics"), null).andExpect(jsonPath("readyDocuments").value(0)).andExpect(jsonPath("processingDocuments").value(1));
        perform(get(BASE + "/" + id + "/download"), null).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"handbook-v2.pdf\"; filename*=UTF-8''handbook-v2.pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(second));
        perform(get(BASE + "/" + id + "/files/" + firstId + "/download"), null).andExpect(status().isOk()).andExpect(content().bytes(first));
        long other = tree(upload(form("Other", handbook, "2024-2025"), file("other.pdf", PDF, pdf("other")))
                .andExpect(status().isCreated()).andReturn()).get("documentId").asLong();
        perform(get(BASE + "/" + other + "/files/" + firstId + "/download"), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("DOCUMENT_FILE_NOT_FOUND"));
        assertThat(secondId).isNotEqualTo(firstId);
        perform(patch(BASE + "/" + id + "/archive"), null).andExpect(status().isOk());
        assertThat(storedFiles()).isEqualTo(3);
        replace(id, file("v3.pdf", PDF, pdf("v3"))).andExpect(status().isConflict()).andExpect(jsonPath("code").value("DOCUMENT_NOT_EDITABLE"));
        perform(get(BASE + "/" + id + "/download"), null).andExpect(status().isOk());
        perform(delete(BASE + "/" + id), null).andExpect(status().isNoContent());
        perform(get(BASE + "/" + id + "/download"), null).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DOCUMENT_NOT_FOUND"));
        assertThat(storedFiles()).isEqualTo(3);
        replace(999_999L, file("x.pdf", PDF, pdf("x"))).andExpect(status().isNotFound());
    }

    @Test
    void concurrentReplacementsNeverCreateTwoCurrentRevisions() throws Exception {
        long id = tree(upload(form("Handbook", handbook, "2024-2025"), file("v1.pdf", PDF, pdf("v1")))
                .andExpect(status().isCreated()).andReturn()).get("documentId").asLong();
        try (var pool = Executors.newFixedThreadPool(3)) {
            var barrier = new CyclicBarrier(3);
            var results = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 3; i++) {
                int n = i;
                results.add(pool.submit(() -> { barrier.await();
                    return replace(id, file("race-" + n + ".pdf", PDF, pdf("race " + n))).andReturn().getResponse().getStatus(); }));
            }
            for (var result : results) assertThat(result.get(20, TimeUnit.SECONDS)).isIn(201, 409);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM academic_document_files WHERE document_id=? AND is_current", Integer.class, id)).isOne();
        int revisions = db.queryForObject("SELECT count(*) FROM academic_document_files WHERE document_id=?", Integer.class, id);
        assertThat(db.queryForObject("SELECT max(revision_number) FROM academic_document_files WHERE document_id=?", Integer.class, id)).isEqualTo(revisions);
        assertThat(storedFiles()).isEqualTo(revisions);
    }

    @Test
    void metadataFailureDeletesTheStoredFileAndStorageFailureSavesNothing() throws Exception {
        db.execute("""
                CREATE FUNCTION knowledge_test_fail() RETURNS trigger LANGUAGE plpgsql AS
                \\$\\$ BEGIN RAISE EXCEPTION 'simulated metadata failure'; END \\$\\$""".replace("\\$", "$"));
        db.execute("CREATE TRIGGER knowledge_test_fail BEFORE INSERT ON academic_document_processing FOR EACH ROW EXECUTE FUNCTION knowledge_test_fail()");
        upload(form("T", handbook, "2024-2025"), file("a.pdf", PDF, pdf("x"))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("code").value("DOCUMENT_METADATA_SAVE_FAILED"));
        assertThat(db.queryForObject("SELECT count(*) FROM academic_documents", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM academic_document_files", Integer.class)).isZero();
        assertThat(storedFiles()).isZero();
        db.execute("DROP TRIGGER knowledge_test_fail ON academic_document_processing");
        clearStorage();
        Path knowledge = KNOWLEDGE_STORAGE.resolve("knowledge");
        Files.createDirectories(knowledge);
        Files.setPosixFilePermissions(knowledge, PosixFilePermissions.fromString("r-x------"));
        try {
            upload(form("T", handbook, "2024-2025"), file("a.pdf", PDF, pdf("x"))).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("code").value("DOCUMENT_STORAGE_FAILED"));
        } finally {
            Files.setPosixFilePermissions(knowledge, PosixFilePermissions.fromString("rwx------"));
        }
        assertThat(db.queryForObject("SELECT count(*) FROM academic_documents", Integer.class)).isZero();
    }

    @Test
    void reconciliationReportsOrphansAndRemovesAbandonedPartialWrites() throws Exception {
        upload(form("Kept", handbook, "2024-2025"), file("kept.pdf", PDF, pdf("kept"))).andExpect(status().isCreated());
        Path orphan = KNOWLEDGE_STORAGE.resolve("knowledge/2020/01/11111111-2222-3333-4444-555555555555.pdf");
        Files.createDirectories(orphan.getParent());
        Files.write(orphan, pdf("orphan"));
        Path part = KNOWLEDGE_STORAGE.resolve(".incoming/abandoned.part");
        Files.write(part, new byte[] {1});
        var old = FileTime.from(clock.instant().minus(Duration.ofHours(2)));
        Files.setLastModifiedTime(orphan, old);
        Files.setLastModifiedTime(part, old);
        try (var files = Files.walk(KNOWLEDGE_STORAGE.resolve("knowledge"))) {
            for (Path path : files.filter(Files::isRegularFile).toList()) Files.setLastModifiedTime(path, old);
        }
        assertThat(reconciliation.reconcile()).isOne();
        assertThat(orphan).exists();
        assertThat(part).doesNotExist();
    }

    @Test
    void uploadDownloadAndReplacementRequireAdministrativeRole() throws Exception {
        long id = tree(upload(form("Handbook", handbook, "2024-2025"), file("v1.pdf", PDF, pdf("v1")))
                .andExpect(status().isCreated()).andReturn()).get("documentId").asLong();
        db.update("INSERT INTO app_roles(role_code,role_name,is_active) VALUES ('KNW_TEST_READER','Knowledge reader',TRUE) ON CONFLICT (role_code) DO NOTHING");
        setRole("KNW_TEST_READER");
        upload(form("T", handbook, "2024-2025"), file("a.pdf", PDF, pdf("x"))).andExpect(status().isForbidden());
        replace(id, file("a.pdf", PDF, pdf("x"))).andExpect(status().isForbidden());
        perform(get(BASE + "/" + id + "/download"), null).andExpect(status().isForbidden());
        perform(get(BASE + "/" + id + "/files"), null).andExpect(status().isForbidden());
        perform(post(BASE + "/" + id + "/reprocess"), null).andExpect(status().isForbidden());
        for (var route : List.of(get(BASE + "/" + id + "/download"), get(BASE + "/" + id + "/processing"), post(BASE + "/upload")))
            mvc.perform(route).andExpect(status().isUnauthorized());
        assertThat(storedFiles()).isOne();
    }

    private ResultActions upload(Map<String, String> fields, MockMultipartFile file) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart(BASE + "/upload");
        if (file != null) builder.file(file);
        fields.forEach(builder::param);
        builder.header("Authorization", "Bearer " + access);
        return mvc.perform(builder);
    }
    private ResultActions replace(long id, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart(BASE + "/" + id + "/files").file(file).header("Authorization", "Bearer " + access));
    }
    private Map<String, String> form(String title, long categoryId, String year) {
        var form = new LinkedHashMap<String, String>();
        form.put("title", title);
        form.put("categoryId", String.valueOf(categoryId));
        form.put("academicYearLabel", year);
        form.put("description", "Official document");
        return form;
    }
    private long storedFiles() throws Exception {
        Path knowledge = KNOWLEDGE_STORAGE.resolve("knowledge");
        if (!Files.exists(knowledge)) return 0;
        try (var files = Files.walk(knowledge)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
    private void clearStorage() throws Exception {
        if (!Files.exists(KNOWLEDGE_STORAGE)) return;
        try (var files = Files.walk(KNOWLEDGE_STORAGE)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList())
                if (!path.equals(KNOWLEDGE_STORAGE)) Files.delete(path);
        }
    }
    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
