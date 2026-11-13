package com.smartAiUniversityAssistant.seniorproject.feature.knowledge;

import com.smartAiUniversityAssistant.seniorproject.config.KnowledgeIngestionProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeDocumentProcessingResponse;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.DocumentIngestionServiceImpl;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base of the Step 14 ingestion tests (spec files 46 to 51). Each test builds DocumentIngestionServiceImpl by hand with
 * the stages it needs and plugs it in behind the HTTP layer through {@link SwitchableIngestion}, so the assertions stay
 * valid whatever stage beans the application context contains. No test relies on a background thread.
 */
@Import(IngestionTestSupport.Wiring.class)
public abstract class IngestionTestSupport extends KnowledgeIntegrationSupport {
    protected static final String BASE = "/api/v1/admin/knowledge/documents";
    @Autowired protected IngestionQueueRepository queueRepository;
    @Autowired protected AcademicDocumentRepository documentRepository;
    @Autowired protected PlatformTransactionManager transactions;
    @Autowired protected KnowledgeIngestionProperties ingestionProperties;
    @Autowired protected SwitchableIngestion switchable;
    /** The EmbeddingClient bean the application's own stages, worker and search use. */
    @Autowired protected DeterministicEmbeddingClient beanClient;
    @Autowired protected FileStorageService storage;
    @Autowired protected com.smartAiUniversityAssistant.seniorproject.config.KnowledgeExtractionProperties extractionProperties;
    @Autowired protected List<DocumentTextExtractor> extractors;
    @Autowired protected AcademicDocumentPageRepository pageRepository;
    @Autowired protected jakarta.persistence.EntityManager entityManager;
    @Autowired protected com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.CleanTextStage cleanStage;
    @Autowired protected com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.SplitIntoChunksStage chunkStage;

    @Autowired protected KnowledgeVectorStore vectorStore;
    @Autowired protected AcademicDocumentChunkRepository chunkRepository;
    @Autowired protected com.smartAiUniversityAssistant.seniorproject.config.KnowledgeEmbeddingProperties embeddingProperties;

    /** The real EMBED stage with a test client and the given batch limits (no wait between requests). */
    protected com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.CreateEmbeddingsStage embedStage(
            EmbeddingClient client, int batchSize, int maxBatchTokens) {
        var p = embeddingProperties;
        var properties = new com.smartAiUniversityAssistant.seniorproject.config.KnowledgeEmbeddingProperties(p.provider(), p.baseUrl(),
                p.apiKey(), p.model(), 768, p.sendDimensions(), p.truncateLongerVectors(), batchSize, maxBatchTokens, p.timeoutSeconds(), p.queryTimeoutSeconds(),
                p.connectTimeoutSeconds(), p.maxRetries(), Duration.ZERO, 0);
        return new com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.CreateEmbeddingsStage(client,
                vectorStore, chunkRepository, properties, transactions);
    }

    /** A text whose chunking gives one chunk per chapter (each chapter has more than min-chars of body). */
    protected static String chapters(int count, String variant) {
        var b = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            b.append("Chapter ").append(i).append(" Topic ").append(i).append("\n\n");
            for (int s = 0; s < 4; s++)
                b.append("Article ").append(i * 10 + s).append(". Students of chapter ").append(i).append(" follow rule ").append(s)
                        .append(" of the sample regulations").append(i == 1 && s == 0 ? variant : "").append(" in every semester.\n");
            b.append('\n');
        }
        return b.toString();
    }

    protected static double[] parseVector(String literal) {
        String[] parts = literal.substring(1, literal.length() - 1).split(",");
        double[] v = new double[parts.length];
        for (int i = 0; i < parts.length; i++) v[i] = Double.parseDouble(parts[i]);
        return v;
    }

    /** The sample document of spec file 47, section 19. */
    protected static String sampleText() throws java.io.IOException {
        return Files.readString(Path.of("docker/dev-fixtures/rag-sample-regulations.txt"));
    }
    protected long categoryId;
    protected final List<String> calls = new CopyOnWriteArrayList<>();
    /** The embedding client of hand-built workers and stages; its model decides which vectors count. */
    protected final DeterministicEmbeddingClient embeddingClient = new DeterministicEmbeddingClient();
    private final List<String> students = new ArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    public static class Wiring {
        @Bean @Primary SwitchableIngestion switchableIngestion(ObjectProvider<DocumentIngestionServiceImpl> real) {
            return new SwitchableIngestion(real);
        }

        /** The application's EmbeddingClient in these tests: no provider is ever called (R12). */
        @Bean @Primary DeterministicEmbeddingClient deterministicEmbeddingClient() { return new DeterministicEmbeddingClient(); }
    }

    /** Delegates to the worker a test built, or to the application's own worker when none is set. */
    public static class SwitchableIngestion implements DocumentIngestionService {
        private final ObjectProvider<DocumentIngestionServiceImpl> real;
        volatile DocumentIngestionService target;
        SwitchableIngestion(ObjectProvider<DocumentIngestionServiceImpl> real) { this.real = real; }
        private DocumentIngestionService delegate() { return target != null ? target : real.getObject(); }
        public void runOnce() { delegate().runOnce(); }
        public KnowledgeDocumentProcessingResponse requestProcessing(long documentId, long actorUserId, ReprocessMode mode) {
            return delegate().requestProcessing(documentId, actorUserId, mode);
        }
        public KnowledgeDocumentProcessingResponse describe(long documentId) { return delegate().describe(documentId); }
        public boolean ingestionAvailable() { return delegate().ingestionAvailable(); }
    }

    @BeforeEach
    void ingestionFixture() throws Exception {
        categoryId = category("REGULATION", "Regulations");
        clearStorage();
        calls.clear();
    }

    @AfterEach
    void ingestionCleanup() throws Exception {
        switchable.target = null;
        beanClient.reset();
        for (String code : students) {
            db.update("DELETE FROM student_credentials WHERE student_id IN (SELECT id FROM students WHERE student_code=?)", code);
            // Step 17: the Student's stored assistant conversations (no cascade, as everywhere in the schema).
            db.update("DELETE FROM assistant_messages WHERE conversation_id IN (SELECT c.id FROM assistant_conversations c "
                    + "JOIN students s ON s.id=c.student_id WHERE s.student_code=?)", code);
            db.update("DELETE FROM assistant_conversations WHERE student_id IN (SELECT id FROM students WHERE student_code=?)", code);
            db.update("DELETE FROM students WHERE student_code=?", code);
        }
        students.clear();
        clearStorage();
    }

    /** Builds a worker with the given stages and makes the HTTP endpoints use it. */
    protected DocumentIngestionServiceImpl worker(List<? extends IngestionStage> stages) {
        return worker(ingestionProperties, stages);
    }

    protected DocumentIngestionServiceImpl worker(KnowledgeIngestionProperties properties, List<? extends IngestionStage> stages) {
        var worker = newWorker(properties, stages);
        switchable.target = worker;
        return worker;
    }

    /** A worker that is not plugged into the HTTP layer (for example a second instance). */
    protected DocumentIngestionServiceImpl newWorker(KnowledgeIngestionProperties properties, List<? extends IngestionStage> stages) {
        return new DocumentIngestionServiceImpl(queueRepository, documentRepository, clock, properties, transactions, List.copyOf(stages),
                vectorStore, embeddingClient);
    }

    /** The real EXTRACT stage with its working copies in the given directory. */
    protected com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage extractStage(Path temp,
            Duration timeout, List<DocumentTextExtractor> using) {
        var p = extractionProperties;
        return new com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage(storage,
                new com.smartAiUniversityAssistant.seniorproject.config.KnowledgeExtractionProperties(p.maxPages(), p.maxChars(),
                        p.unitMaxChars(), timeout, p.pdfSortByPosition(), temp.toString()),
                ingestionProperties, using, pageRepository, entityManager, transactions, clock);
    }

    protected com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.ExtractTextStage extractStage(Path temp) {
        return extractStage(temp, Duration.ofMinutes(5), extractors);
    }

    protected KnowledgeIngestionProperties properties(boolean enabled, boolean autoQueue, int maxAttempts, Duration stageTimeout) {
        var p = ingestionProperties;
        return new KnowledgeIngestionProperties(enabled, false, autoQueue, p.pollInterval(), maxAttempts, p.retryInitialDelay(),
                p.retryMultiplier(), p.retryMaxDelay(), p.staleAfter(), stageTimeout);
    }

    /** A stage that records "TYPE:fileId" and then runs the given behaviour. */
    protected FakeStage fake(IngestionStageType type) { return new FakeStage(type, calls, (job, control) -> {}); }
    protected FakeStage fake(IngestionStageType type, BiConsumer<IngestionJob, IngestionJobControl> behaviour) {
        return new FakeStage(type, calls, behaviour);
    }
    protected List<IngestionStage> allFakes() {
        return Arrays.stream(IngestionStageType.values()).map(t -> (IngestionStage) fake(t)).toList();
    }

    public record FakeStage(IngestionStageType type, List<String> calls, BiConsumer<IngestionJob, IngestionJobControl> behaviour)
            implements IngestionStage {
        public void execute(IngestionJob job, IngestionJobControl control) {
            calls.add(type + ":" + job.fileId());
            behaviour.accept(job, control);
        }
    }

    // ---------- HTTP helpers ----------

    protected long upload(String filename, String contentType, byte[] content) throws Exception {
        return upload(filename, contentType, content, Map.of());
    }

    protected long upload(String filename, String contentType, byte[] content, Map<String, String> extra) throws Exception {
        var builder = multipart(BASE + "/upload").file(new MockMultipartFile("file", filename, contentType, content));
        builder.param("title", "Sample " + filename);
        builder.param("categoryId", String.valueOf(categoryId));
        builder.param("academicYearLabel", "2026-2027");
        extra.forEach(builder::param);
        builder.header("Authorization", "Bearer " + access);
        return tree(mvc.perform(builder).andExpect(status().isCreated()).andReturn()).get("documentId").asLong();
    }

    protected long uploadText(String text) throws Exception {
        return upload("sample.txt", KnowledgeTestFiles.TXT, text.getBytes(StandardCharsets.UTF_8));
    }

    protected ResultActions replaceFile(long documentId, String filename, String contentType, byte[] content) throws Exception {
        return mvc.perform(multipart(BASE + "/" + documentId + "/files")
                .file(new MockMultipartFile("file", filename, contentType, content)).header("Authorization", "Bearer " + access));
    }

    protected ResultActions reprocess(long documentId, String body) throws Exception {
        var builder = post(BASE + "/" + documentId + "/reprocess").header("Authorization", "Bearer " + access);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(builder);
    }

    protected long currentFile(long documentId) {
        return db.queryForObject("SELECT id FROM academic_document_files WHERE document_id=? AND is_current", Long.class, documentId);
    }

    protected Map<String, Object> processingRow(long fileId) {
        return db.queryForMap("SELECT * FROM academic_document_processing WHERE file_id=?", fileId);
    }

    protected String statusOf(long fileId) {
        return db.queryForObject("SELECT status FROM academic_document_processing WHERE file_id=?", String.class, fileId);
    }

    protected long pages(long fileId) {
        return db.queryForObject("SELECT count(*) FROM academic_document_pages WHERE file_id=?", Long.class, fileId);
    }

    protected long chunks(long fileId) {
        return db.queryForObject("SELECT count(*) FROM academic_document_chunks WHERE file_id=?", Long.class, fileId);
    }

    protected void insertPage(long fileId, int number, String text) {
        db.update("INSERT INTO academic_document_pages(file_id,page_number,unit_type,raw_text,raw_char_count) VALUES (?,?,'TEXT',?,?)",
                fileId, number, text, text.length());
    }

    protected void insertChunk(long fileId, int index, String text) {
        db.update("""
                INSERT INTO academic_document_chunks(file_id,chunk_index,content,content_hash,char_count,token_estimate,page_start,page_end)
                VALUES (?,?,?,repeat('e',64),?,1,1,1)""", fileId, index, text, text.length());
    }

    /** A signed-in Student without program or department; removed again after the test. */
    protected String studentToken(String code) throws Exception {
        long id = db.queryForObject("""
                INSERT INTO students(student_code,university_email,first_name,last_name,account_status)
                VALUES (?,?,'Rag','Student','ACTIVE') RETURNING id""", Long.class, code, code.toLowerCase(Locale.ROOT) + "@rsu.ac.th");
        students.add(code);
        db.update("INSERT INTO student_credentials(student_id,password_hash,force_password_change) VALUES (?,?,FALSE)", id, encoder.encode(PASSWORD));
        var result = mvc.perform(post("/api/v1/student/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("universityEmail", code.toLowerCase(Locale.ROOT) + "@rsu.ac.th", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return tree(result).get("accessToken").asText();
    }

    protected void clearStorage() throws Exception {
        if (!Files.exists(KNOWLEDGE_STORAGE)) return;
        try (var files = Files.walk(KNOWLEDGE_STORAGE)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList())
                if (!path.equals(KNOWLEDGE_STORAGE)) Files.delete(path);
        }
    }

    protected Path storedPath(long fileId) {
        return KNOWLEDGE_STORAGE.resolve(db.queryForObject("SELECT storage_key FROM academic_document_files WHERE id=?", String.class, fileId));
    }
}
