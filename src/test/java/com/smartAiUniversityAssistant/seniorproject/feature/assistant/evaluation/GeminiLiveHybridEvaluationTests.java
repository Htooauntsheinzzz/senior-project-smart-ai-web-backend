package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.ExternalLlmClient;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.DocumentIngestionService;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.EmbeddingClient;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Step 16 live check (spec file 57, section 17): HYB-001, HYB-003, HYB-004 and HYB-026 for Student A through the real
 * endpoint with the real chat model, the real embedding model, the progress tool and hybrid answers on (AcademicProgressFixture).
 * Opt-in only; it needs both variables:
 *
 *   AI_EVAL_ENABLED=true KNOWLEDGE_EVAL_ENABLED=true ./mvnw test -Dtest=GeminiLiveHybridEvaluationTests
 *
 * Keys come from the environment or the git-ignored .env. Budget: AI_EVAL_MAX_PROVIDER_CALLS (default 10 for this class)
 * chat requests; the two sample documents and four questions use at most 6 embedding requests. AI_EVAL_DELAY_SECONDS
 * (default 15) between cases. Fails only on a security finding (another program's data or a key in the report); answer
 * quality is reported, not asserted. Report: target/step16-hybrid-live/. The implementing agent did not run it.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "AI_EVAL_ENABLED", matches = "true")
@org.springframework.context.annotation.Import(GeminiLiveHybridEvaluationTests.Progress.class)
class GeminiLiveHybridEvaluationTests extends IntentEvaluationSupport {
    private static final Map<String, String> DOT_ENV = readDotEnv();
    private static final List<String> CASES = List.of("HYB-001", "HYB-003", "HYB-004", "HYB-026");
    @Autowired ExternalLlmClient gemini;
    @Autowired AiAssistantProperties properties;
    @Autowired EmbeddingClient embeddings;
    @Autowired DocumentIngestionService ingestion;

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class Progress {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings liveHybridSettings() {
            return com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.AssistantProgressSettings.of(true, true);
        }
    }

    @Autowired com.smartAiUniversityAssistant.seniorproject.feature.studentacademic.service.AcademicProgressService progressService;

    @DynamicPropertySource
    static void liveModels(DynamicPropertyRegistry r) {
        for (String name : List.of("AI_PROVIDER", "AI_API_KEY", "AI_MODEL", "AI_BASE_URL", "AI_TIMEOUT_SECONDS", "AI_TEMPERATURE")) {
            String value = setting(name, null);
            if (value != null) r.add("app.ai." + name.substring(3).toLowerCase(Locale.ROOT).replace('_', '-'), () -> value);
        }
        for (String name : List.of("AI_EMBEDDING_PROVIDER", "AI_EMBEDDING_BASE_URL", "AI_EMBEDDING_API_KEY", "AI_EMBEDDING_MODEL",
                "AI_EMBEDDING_SEND_DIMENSIONS", "AI_EMBEDDING_BATCH_SIZE")) {
            String value = setting(name, null);
            if (value != null) r.add("app.knowledge.embedding." + name.substring(13).toLowerCase(Locale.ROOT).replace('_', '-'), () -> value);
        }
        r.add("app.ai.max-retries", () -> "0");
    }

    @Test
    void liveHybridEvaluation() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("KNOWLEDGE_EVAL_ENABLED")), "KNOWLEDGE_EVAL_ENABLED=true is also required");
        assertThat(properties.configured()).as("AI_API_KEY, AI_MODEL and AI_BASE_URL must be set").isTrue();
        assertThat(embeddings.configured()).as("AI_EMBEDDING_MODEL must be set").isTrue();
        int maxCalls = Integer.parseInt(setting("AI_EVAL_MAX_PROVIDER_CALLS", "10"));
        long delayMs = Long.parseLong(setting("AI_EVAL_DELAY_SECONDS", "15")) * 1000;
        var dataset = HybridEvaluationDataset.get();
        var progressFixture = new com.smartAiUniversityAssistant.seniorproject.feature.assistant.AcademicProgressFixture(db);
        progressFixture.clean();
        createFixture();
        progressFixture.create(fixture, progressService);
        long[] documents = uploadSamples(dataset.regulationsTitle(), dataset.curriculumTitle());
        recorder.delegateTo(gemini);
        recorder.maxCalls(maxCalls);
        var rows = new ArrayList<Map<String, Object>>();
        boolean securityFinding = false;
        try {
            for (var c : dataset.cases().stream().filter(c -> CASES.contains(c.caseId())).toList()) {
                if (recorder.totalCalls() + 4 > maxCalls) break;
                if (!rows.isEmpty()) Thread.sleep(delayMs);
                var trace = send(c.testStudent(), c.input(), null).trace();
                var sources = new ArrayList<String>();
                var body = lastBody();
                if (body != null && body.get("sources") != null) body.get("sources").forEach(s -> sources.add(s.get("title").asText()));
                var row = new LinkedHashMap<String, Object>();
                row.put("caseId", c.caseId());
                row.put("backendProgressCall", !trace.toolCalls().isEmpty() && trace.toolCalls().getFirst().round() == 0);
                row.put("searchRan", trace.toolNames().contains("search_university_knowledge"));
                row.put("replaced", c.lastText() != null && !String.valueOf(trace.message()).equals(c.lastText().strip()));
                row.put("audiences", audiences(trace.toolResultsJson()));
                row.put("sources", sources);
                row.put("mustContainFound", c.mustContain().stream().filter(v -> IntentEvaluator.containsToken(trace.message(), v)).toList());
                row.put("providerRequests", trace.providerRequests());
                row.put("message", trace.message());
                securityFinding |= trace.toolResultsJson().contains(properties.apiKey()) || String.valueOf(trace.message()).contains(properties.apiKey())
                        || recorder.requests().stream().map(Object::toString).anyMatch(r -> r.contains(c.testStudent())
                        || r.toLowerCase(Locale.ROOT).contains(c.testStudent().toLowerCase(Locale.ROOT) + "@rsu.ac.th"));
                rows.add(row);
            }
        } finally {
            db.update("DELETE FROM academic_document_chunks");
            db.update("DELETE FROM academic_document_pages");
            db.update("DELETE FROM academic_document_processing");
            db.update("DELETE FROM academic_document_files");
            db.update("DELETE FROM academic_documents WHERE id IN (?, ?)", documents[0], documents[1]);
            db.update("DELETE FROM document_categories WHERE category_code='S16L'");
            progressFixture.clean();
            fixture.clean();
            var report = new LinkedHashMap<String, Object>();
            report.put("mode", "LIVE_GEMINI");
            report.put("model", properties.model());
            report.put("embeddingModel", embeddings.model());
            report.put("runAt", Instant.now().toString());
            report.put("chatRequests", recorder.totalCalls());
            report.put("cases", rows);
            Path dir = Path.of("target/step16-hybrid-live");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("report.json"), JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
        assertThat(securityFinding).isFalse();
    }

    private static List<String> audiences(String toolResultsJson) {
        var out = new ArrayList<String>();
        var m = java.util.regex.Pattern.compile("\"appliesTo\":\"([^\"]+)\"").matcher(toolResultsJson);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private long[] uploadSamples(String regulationsTitle, String curriculumTitle) throws Exception {
        long admin = db.queryForObject("""
                INSERT INTO app_users(employee_id,first_name,last_name,email,account_status)
                VALUES ('S16-LIVE','Curriculum','Live','hybrid.live@example.test','ACTIVE') RETURNING id""", Long.class);
        db.update("INSERT INTO appuser_credentials(user_id,password_hash,force_password_change) VALUES (?,?,FALSE)", admin,
                encoder.encode("curriculum live admin password"));
        db.update("INSERT INTO app_user_roles(user_id,role_id) SELECT ?,id FROM app_roles WHERE role_code='SUPER_ADMIN'", admin);
        var login = mvc.perform(post("/api/v1/admin/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                Map.of("email", "hybrid.live@example.test", "password", "curriculum live admin password")))).andReturn().getResponse();
        String token = json.readTree(login.getContentAsByteArray()).get("accessToken").asText();
        long category = db.queryForObject("INSERT INTO document_categories(category_code,category_name,created_by) VALUES ('S16L','Curriculum',?) RETURNING id",
                Long.class, admin);
        long program = db.queryForObject("SELECT program_id FROM students WHERE student_code='STD-TEST-001'", Long.class);
        long r = upload(token, category, regulationsTitle, "docker/dev-fixtures/rag-sample-regulations.txt", null);
        long c = upload(token, category, curriculumTitle, "docker/dev-fixtures/rag-sample-curriculum-cs.txt", program);
        return new long[] {r, c};
    }

    private long upload(String token, long category, String title, String file, Long program) throws Exception {
        var builder = multipart("/api/v1/admin/knowledge/documents/upload").file(new MockMultipartFile("file",
                Path.of(file).getFileName().toString(), "text/plain", Files.readAllBytes(Path.of(file))));
        builder.param("title", title).param("categoryId", String.valueOf(category)).param("academicYearLabel", "2026-2027");
        if (program != null) builder.param("programId", String.valueOf(program));
        builder.header("Authorization", "Bearer " + token);
        long id = json.readTree(mvc.perform(builder).andReturn().getResponse().getContentAsByteArray()).get("documentId").asLong();
        ingestion.runOnce();
        return id;
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = DOT_ENV.get(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static Map<String, String> readDotEnv() {
        var values = new HashMap<String, String>();
        Path file = Path.of(".env");
        if (!Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file)) {
                int eq = line.indexOf('=');
                if (line.isBlank() || line.strip().startsWith("#") || eq <= 0) continue;
                values.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip().replaceAll("^\"|\"$", ""));
            }
        } catch (Exception e) { throw new IllegalStateException("cannot read .env", e); }
        return values;
    }
}
