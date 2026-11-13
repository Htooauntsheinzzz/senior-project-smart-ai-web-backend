package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.dto.KnowledgeSearchRequest;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.embedding.LiveEmbeddingCheck;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.repository.QueryEmbeddingCache;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.search.KnowledgeRetrievalEvaluationDataset.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.*;
import com.smartAiUniversityAssistant.seniorproject.security.AuthenticatedUser;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

/**
 * Opt-in live retrieval evaluation with the real embedding model (spec file 52, section 18.3). Skipped unless
 * KNOWLEDGE_EVAL_ENABLED=true is set in the shell; never part of a normal run or CI. One request for the document and one
 * per case. Writes target/knowledge-eval/retrieval-report.json and .md. Fails only when the run itself breaks.
 *
 *   KNOWLEDGE_EVAL_ENABLED=true ./mvnw test -Dtest=KnowledgeLiveRetrievalEvaluationTests
 */
@EnabledIfEnvironmentVariable(named = "KNOWLEDGE_EVAL_ENABLED", matches = "true")
class KnowledgeLiveRetrievalEvaluationTests extends IngestionTestSupport {
    @TempDir Path temp;
    @Autowired NamedParameterJdbcTemplate named;
    @Autowired KnowledgeSearchProperties searchProperties;
    @Autowired KnowledgeCleaningProperties cleaningProperties;

    record Row(String caseId, String group, String expectation, Integer position, Double expectedScore, Double topScore) {}

    @Test
    void evaluate() throws Exception {
        var env = LiveEmbeddingCheck.settings();
        var cases = KnowledgeRetrievalEvaluationDataset.CASES;
        int maxRequests = Integer.parseInt(env.getOrDefault("KNOWLEDGE_EVAL_MAX_REQUESTS", System.getenv().getOrDefault("KNOWLEDGE_EVAL_MAX_REQUESTS", "40")));
        assertThat(cases.size() + 2).as("cases plus the two document requests must fit KNOWLEDGE_EVAL_MAX_REQUESTS").isLessThanOrEqualTo(maxRequests);
        long intervalMs = Long.parseLong(env.getOrDefault("AI_EMBEDDING_MIN_INTERVAL_MS", "1000"));
        var chat = new AiAssistantProperties(env.getOrDefault("AI_PROVIDER", "openai-compatible"), env.get("AI_API_KEY"),
                env.getOrDefault("AI_MODEL", "unused"), env.get("AI_BASE_URL"), 30, 10, 0.2, 512, 0, Duration.ZERO, 4, 8, 120);
        var properties = new KnowledgeEmbeddingProperties(env.get("AI_EMBEDDING_PROVIDER"), env.get("AI_EMBEDDING_BASE_URL"),
                env.get("AI_EMBEDDING_API_KEY"), env.get("AI_EMBEDDING_MODEL"), 768, !"false".equals(env.get("AI_EMBEDDING_SEND_DIMENSIONS")), !"false".equals(env.get("AI_EMBEDDING_TRUNCATE_LONGER_VECTORS")),
                16, 8000, 30, 10, 10, 1, Duration.ofSeconds(2), 0);
        var live = new OpenAiCompatibleEmbeddingClient(properties, chat, JsonMapper.builder().build());
        assertThat(live.configured()).isTrue();

        var worker = worker(List.of(extractStage(temp), cleanStage, chunkStage, embedStage(live, 16, 8000),
                new FinalizeIndexStage(vectorStore, live, named)));
        // Spec file 55, section 12: both sample documents; each case is searched in the document it names.
        var docs = new EnumMap<Document, Long>(Document.class);
        for (Document d : Document.values()) {
            long id = upload(Path.of(d.path).getFileName().toString(), KnowledgeTestFiles.TXT, KnowledgeRealFiles.utf8(Files.readString(Path.of(d.path))));
            worker.runOnce();
            assertThat(statusOf(currentFile(id))).isEqualTo("READY");
            docs.put(d, id);
        }

        var search = new KnowledgeSearchServiceImpl(live, vectorStore, new QueryEmbeddingCache(null, Duration.ZERO), searchProperties,
                cleaningProperties, null);
        var actor = new AuthenticatedUser(actorId, "s", "r", false, Set.of("SUPER_ADMIN"));
        var rows = new ArrayList<Row>();
        for (Case c : cases) {
            Thread.sleep(intervalMs);
            var results = search.adminSearch(actor, new KnowledgeSearchRequest(c.question(), 5, 0.0, null, null, docs.get(c.document()), null)).results();
            Double top = results.isEmpty() ? null : results.get(0).score();
            Integer position = null;
            Double expected = null;
            if (c.expectation() == Expectation.FOUND)
                for (var r : results) if (r.content().contains(c.mustContain())) { position = r.rank(); expected = r.score(); break; }
            rows.add(new Row(c.caseId(), c.group(), c.expectation().name(), position, expected, top));
        }
        write(rows, live.model());
    }

    private static void write(List<Row> rows, String model) throws Exception {
        var groups = new LinkedHashMap<String, List<Row>>();
        groups.put("ALL", rows);
        for (Row r : rows) groups.computeIfAbsent(r.group(), g -> new ArrayList<>()).add(r);
        var metrics = new LinkedHashMap<String, Map<String, Object>>();
        groups.forEach((group, list) -> {
            var found = list.stream().filter(r -> r.expectation().equals("FOUND")).toList();
            var m = new LinkedHashMap<String, Object>();
            for (int k : new int[] {1, 3, 5})
                m.put("hit@" + k, found.isEmpty() ? null : found.stream().filter(r -> r.position() != null && r.position() <= k).count() / (double) found.size());
            m.put("MRR", found.isEmpty() ? null : found.stream().mapToDouble(r -> r.position() == null ? 0 : 1.0 / r.position()).average().orElse(0));
            m.put("lowestExpectedScore", found.stream().filter(r -> r.expectedScore() != null).mapToDouble(Row::expectedScore).min().stream().boxed().findFirst().orElse(null));
            m.put("highestUnansweredScore", list.stream().filter(r -> r.expectation().equals("NOT_FOUND") && r.topScore() != null)
                    .mapToDouble(Row::topScore).max().stream().boxed().findFirst().orElse(null));
            metrics.put(group, m);
        });
        Path dir = Path.of("target/knowledge-eval");
        Files.createDirectories(dir);
        var report = new LinkedHashMap<String, Object>();
        report.put("model", model);
        report.put("metrics", metrics);
        report.put("cases", rows);
        Files.writeString(dir.resolve("retrieval-report.json"), JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        var md = new StringBuilder("# Knowledge retrieval evaluation\n\nModel: " + model + "\n\n| Group | Metric | Value |\n|---|---|---|\n");
        metrics.forEach((g, m) -> m.forEach((k, v) -> md.append("| ").append(g).append(" | ").append(k).append(" | ").append(v).append(" |\n")));
        md.append("\n| Case | Expectation | Position | Expected score | Top score |\n|---|---|---|---|---|\n");
        for (Row r : rows) md.append("| ").append(r.caseId()).append(" | ").append(r.expectation()).append(" | ").append(r.position())
                .append(" | ").append(r.expectedScore()).append(" | ").append(r.topScore()).append(" |\n");
        Files.writeString(dir.resolve("retrieval-report.md"), md.toString());
        System.out.println("KNOWLEDGE_EVAL metrics=" + metrics);
    }
}
