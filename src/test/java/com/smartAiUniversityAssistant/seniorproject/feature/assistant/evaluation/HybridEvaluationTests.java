package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.dto.AcademicProgressQueryResult;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.AcademicProgressQueryService;
import com.smartAiUniversityAssistant.seniorproject.feature.academicquery.service.impl.AcademicProgressQueryServiceImpl;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.AcademicProgressFixture;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.DeterministicEmbeddingClient;
import com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.AcademicProgressService;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.DocumentIngestionService;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Step 16 hybrid evaluation (spec file 57, section 16) on the Step 9 framework: every case of step16/hybrid-cases.jsonl
 * through the real endpoint with the knowledge tool (DeterministicEmbeddingClient), the progress tool and hybrid answers
 * on, AcademicProgressFixture, and the model replaced by the case's script. A @Primary wrapper lets the progress query
 * fail for the PROGRESS_UNAVAILABLE cases. Report: target/step16-hybrid-evaluation/ — results of a scripted model, not of
 * Gemini.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import({HybridEvaluationTests.Embeddings.class, HybridEvaluationTests.Progress.class})
@TestPropertySource(properties = "app.knowledge.search.min-score=0.30")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HybridEvaluationTests extends IntentEvaluationSupport {
    private static final HybridEvaluationDataset DATASET = HybridEvaluationDataset.get();
    private static final String ADMIN_EMAIL = "hybrid.eval.admin@example.test";
    private static final String ADMIN_PASSWORD = "curriculum evaluation admin password";
    private final HybridEvaluator evaluator = new HybridEvaluator(DATASET);
    private final List<HybridEvaluator.Result> results = Collections.synchronizedList(new ArrayList<>());
    @Autowired DocumentIngestionService ingestion;
    private long admin;
    private final List<Long> documents = new ArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Embeddings {
        /** No provider is ever called (R12); the knowledge tool is active because this client is configured. */
        @Bean @Primary DeterministicEmbeddingClient hybridEvaluationEmbeddingClient() { return new DeterministicEmbeddingClient(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Progress {
        @Bean @Primary AssistantProgressSettings hybridProgressSettings() { return AssistantProgressSettings.of(true, true); }
        @Bean @Primary FailingProgressQuery hybridFailingProgressQuery(AcademicProgressQueryServiceImpl delegate) {
            return new FailingProgressQuery(delegate);
        }
    }

    /** The real progress query, except when a case simulates a database outage. */
    static final class FailingProgressQuery implements AcademicProgressQueryService {
        private final AcademicProgressQueryService delegate;
        volatile boolean failing;
        FailingProgressQuery(AcademicProgressQueryService delegate) { this.delegate = delegate; }
        @Override public AcademicProgressQueryResult getMyAcademicProgress() {
            if (failing) throw new org.springframework.dao.DataAccessResourceFailureException("simulated database outage");
            return delegate.getMyAcademicProgress();
        }
    }

    @Autowired FailingProgressQuery outage;
    @Autowired AcademicProgressService progressService;
    private AcademicProgressFixture progressFixture;

    static Stream<Named<HybridEvaluationCase>> cases() {
        return DATASET.cases().stream().map(c -> Named.of(c.caseId() + " " + c.input(), c));
    }

    @BeforeAll
    void createData() throws Exception {
        progressFixture = new AcademicProgressFixture(db);
        progressFixture.clean();
        createFixture();
        progressFixture.create(fixture, progressService);
        clearKnowledge();
        admin = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('S16-ADMIN','Curriculum','Admin',?,'ACTIVE') RETURNING id""", Long.class, ADMIN_EMAIL);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", admin,
                encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='SUPER_ADMIN'", admin);
        var login = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)))).andReturn().getResponse();
        String token = json.readTree(login.getContentAsByteArray()).get("accessToken").asText();
        long category = db.queryForObject("INSERT INTO document_categories(category_code,category_name,created_by) VALUES ('S16','Curriculum',?) RETURNING id",
                Long.class, admin);
        long program = db.queryForObject("SELECT program_id FROM students WHERE student_code='STD-TEST-001'", Long.class);
        documents.add(upload(token, category, DATASET.regulationsTitle(), "docker/dev-fixtures/rag-sample-regulations.txt", null));
        documents.add(upload(token, category, DATASET.curriculumTitle(), "docker/dev-fixtures/rag-sample-curriculum-cs.txt", program));
        for (long document : documents)
            assertThat(db.queryForObject("""
                    SELECT p.status FROM academic_document_processing p JOIN academic_document_files f ON f.id = p.file_id
                    WHERE f.document_id = ? AND f.is_current""", String.class, document)).isEqualTo("READY");
    }

    private long upload(String token, long category, String title, String file, Long program) throws Exception {
        var builder = multipart("/api/v1/admin/knowledge/documents/upload").file(new MockMultipartFile("file",
                Path.of(file).getFileName().toString(), "text/plain", Files.readAllBytes(Path.of(file))));
        builder.param("title", title).param("categoryId", String.valueOf(category)).param("academicYearLabel", "2026-2027");
        if (program != null) builder.param("programId", String.valueOf(program));
        builder.header("Authorization", "Bearer " + token);
        var response = mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        long id = json.readTree(response.getContentAsByteArray()).get("documentId").asLong();
        ingestion.runOnce();
        return id;
    }

    private void clearKnowledge() {
        db.update("DELETE FROM academic_document_chunks");
        db.update("DELETE FROM academic_document_pages");
        db.update("DELETE FROM academic_document_processing");
        db.update("DELETE FROM academic_document_files");
        db.update("DELETE FROM academic_documents");
        db.update("DELETE FROM document_categories");
    }

    @AfterAll
    void writeReport() throws Exception {
        clearKnowledge();
        progressFixture.clean();
        fixture.clean();
        var byCategory = new TreeMap<String, Map<String, Object>>();
        for (var r : results) {
            var m = byCategory.computeIfAbsent(r.category(), k -> new LinkedHashMap<>(Map.of("cases", 0, "passed", 0)));
            m.put("cases", (Integer) m.get("cases") + 1);
            if (r.passed()) m.put("passed", (Integer) m.get("passed") + 1);
        }
        byCategory.values().forEach(m -> m.put("passRate", 100.0 * (Integer) m.get("passed") / (Integer) m.get("cases")));
        var summary = new LinkedHashMap<String, Object>();
        summary.put("mode", "DETERMINISTIC_SCRIPTED_MODEL");
        summary.put("note", "Results of a scripted stand-in model through the real backend; not a measurement of Gemini.");
        summary.put("datasetVersion", DATASET.version());
        summary.put("runAt", Instant.now().toString());
        summary.put("cases", results.size());
        summary.put("passed", results.stream().filter(HybridEvaluator.Result::passed).count());
        summary.put("passRatePerCategory", byCategory);
        summary.put("faultyAnswersReplaced", results.stream().filter(HybridEvaluator.Result::faultyReplaced).count());
        summary.put("crossProgramFindings", results.stream().filter(HybridEvaluator.Result::crossProgramFinding).count());
        summary.put("crossStudentFindings", results.stream().filter(HybridEvaluator.Result::crossStudentFinding).count());
        summary.put("identityFindings", results.stream().filter(HybridEvaluator.Result::identityFinding).count());
        summary.put("knownLimits", results.stream().filter(HybridEvaluator.Result::knownLimit).map(HybridEvaluator.Result::caseId).toList());
        Path directory = Path.of("target", "step16-hybrid-evaluation");
        Files.createDirectories(directory);
        var mapper = JsonMapper.builder().build();
        Files.writeString(directory.resolve("summary.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
        var lines = new StringBuilder();
        for (var r : results) lines.append(mapper.writeValueAsString(r)).append('\n');
        Files.writeString(directory.resolve("case-results.jsonl"), lines.toString(), StandardCharsets.UTF_8);
        if (results.size() == DATASET.cases().size()) {
            assertThat(summary.get("crossProgramFindings")).isEqualTo(0L);
            assertThat(summary.get("crossStudentFindings")).isEqualTo(0L);
            assertThat(summary.get("identityFindings")).isEqualTo(0L);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void hybridCase(HybridEvaluationCase c) throws Exception {
        recorder.delegateTo(new ScriptedLlmClient(c.script()));
        outage.failing = c.category() == HybridEvaluationCase.Category.PROGRESS_UNAVAILABLE;
        Turn turn;
        try {
            turn = send(c.testStudent(), c.input(), null);
        } finally {
            outage.failing = false;
        }
        var sources = new ArrayList<String>();
        var body = lastBody();
        if (body != null && body.get("sources") != null) body.get("sources").forEach(s -> sources.add(s.get("title").asText()));
        var result = evaluator.evaluate(c, turn.trace(), sources, recorder.requests(), c.testStudent().toLowerCase(java.util.Locale.ROOT) + "@rsu.ac.th");
        results.add(result);
        assertThat(result.failures()).as(c.caseId() + ": " + turn.trace().message() + " sources=" + sources + " tools="
                + turn.trace().toolNames() + " requests=" + turn.trace().providerRequests()).isEmpty();
        assertThat(turn.trace().dataModified()).isFalse();
    }
}
