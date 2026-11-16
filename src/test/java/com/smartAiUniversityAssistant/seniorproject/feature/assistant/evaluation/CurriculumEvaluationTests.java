package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.DeterministicEmbeddingClient;
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
 * Step 15 curriculum evaluation (spec file 55, section 11) on the Step 9 framework: every case of
 * step15/curriculum-cases.jsonl through the real endpoint, JWT, tool executor, semantic search, pgvector and PostgreSQL,
 * with the model replaced by the case's script and DeterministicEmbeddingClient as the EmbeddingClient bean (so the
 * knowledge tool is active). Report: target/step15-curriculum-evaluation/ — results of a scripted model, not of Gemini.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(CurriculumEvaluationTests.Embeddings.class)
@TestPropertySource(properties = "app.knowledge.search.min-score=0.30")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CurriculumEvaluationTests extends IntentEvaluationSupport {
    private static final CurriculumEvaluationDataset DATASET = CurriculumEvaluationDataset.get();
    private static final String ADMIN_EMAIL = "curriculum.eval.admin@example.test";
    private static final String ADMIN_PASSWORD = "curriculum evaluation admin password";
    private final CurriculumEvaluator evaluator = new CurriculumEvaluator(DATASET);
    private final List<CurriculumEvaluator.Result> results = Collections.synchronizedList(new ArrayList<>());
    @Autowired DocumentIngestionService ingestion;
    private long admin;
    private final List<Long> documents = new ArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Embeddings {
        /** No provider is ever called (R12); the knowledge tool is active because this client is configured. */
        @Bean @Primary DeterministicEmbeddingClient curriculumEvaluationEmbeddingClient() { return new DeterministicEmbeddingClient(); }
    }

    static Stream<Named<CurriculumEvaluationCase>> cases() {
        return DATASET.cases().stream().map(c -> Named.of(c.caseId() + " " + c.input(), c));
    }

    @BeforeAll
    void createData() throws Exception {
        createFixture();
        clearKnowledge();
        admin = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('S15-ADMIN','Curriculum','Admin',?,'ACTIVE') RETURNING id""", Long.class, ADMIN_EMAIL);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", admin,
                encoder.encode(ADMIN_PASSWORD));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='SUPER_ADMIN'", admin);
        var login = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD)))).andReturn().getResponse();
        String token = json.readTree(login.getContentAsByteArray()).get("accessToken").asText();
        long category = db.queryForObject("INSERT INTO document_categories(category_code,category_name,created_by) VALUES ('S15','Curriculum',?) RETURNING id",
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
        summary.put("passed", results.stream().filter(CurriculumEvaluator.Result::passed).count());
        summary.put("passRatePerCategory", byCategory);
        summary.put("faultyAnswersReplaced", results.stream().filter(CurriculumEvaluator.Result::faultyReplaced).count());
        summary.put("crossProgramFindings", results.stream().filter(CurriculumEvaluator.Result::crossProgramFinding).count());
        summary.put("knownLimits", results.stream().filter(CurriculumEvaluator.Result::knownLimit).map(CurriculumEvaluator.Result::caseId).toList());
        Path directory = Path.of("target", "step15-curriculum-evaluation");
        Files.createDirectories(directory);
        var mapper = JsonMapper.builder().build();
        Files.writeString(directory.resolve("summary.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
        var lines = new StringBuilder();
        for (var r : results) lines.append(mapper.writeValueAsString(r)).append('\n');
        Files.writeString(directory.resolve("case-results.jsonl"), lines.toString(), StandardCharsets.UTF_8);
        if (results.size() == DATASET.cases().size())
            assertThat(summary.get("crossProgramFindings")).isEqualTo(0L);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void curriculumCase(CurriculumEvaluationCase c) throws Exception {
        recorder.delegateTo(new ScriptedLlmClient(c.script()));
        Turn turn = send(c.testStudent(), c.input(), null);
        var sources = new ArrayList<String>();
        var body = lastBody();
        if (body != null && body.get("sources") != null) body.get("sources").forEach(s -> sources.add(s.get("title").asText()));
        var result = evaluator.evaluate(c, turn.trace(), sources);
        results.add(result);
        assertThat(result.failures()).as(c.caseId() + ": " + turn.trace().message() + " sources=" + sources + " tools="
                + turn.trace().toolNames() + " requests=" + turn.trace().providerRequests()).isEmpty();
        assertThat(turn.trace().dataModified()).isFalse();
    }
}
