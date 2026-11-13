package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.embedding;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.OpenAiCompatibleEmbeddingClient;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

/**
 * Opt-in live check of the real embedding provider (spec file 50, section 15). Skipped unless KNOWLEDGE_EVAL_ENABLED=true
 * is set in the shell; never part of a normal run. Exactly one request with three texts. Reads AI_EMBEDDING_* / AI_*
 * from the environment or the git-ignored .env file; prints the model id, the duration and three similarities, never a key.
 *
 *   KNOWLEDGE_EVAL_ENABLED=true ./mvnw test -Dtest=LiveEmbeddingCheck
 */
@EnabledIfEnvironmentVariable(named = "KNOWLEDGE_EVAL_ENABLED", matches = "true")
public class LiveEmbeddingCheck {

    @Test
    void oneRequestWithThreeTexts() throws Exception {
        var env = settings();
        var chat = new AiAssistantProperties(env.getOrDefault("AI_PROVIDER", "openai-compatible"), env.get("AI_API_KEY"),
                env.getOrDefault("AI_MODEL", "unused"), env.get("AI_BASE_URL"), 30, 10, 0.2, 512, 0, Duration.ZERO, 4, 8, 120);
        var properties = new KnowledgeEmbeddingProperties(env.get("AI_EMBEDDING_PROVIDER"), env.get("AI_EMBEDDING_BASE_URL"),
                env.get("AI_EMBEDDING_API_KEY"), env.get("AI_EMBEDDING_MODEL"), 768,
                !"false".equals(env.get("AI_EMBEDDING_SEND_DIMENSIONS")), !"false".equals(env.get("AI_EMBEDDING_TRUNCATE_LONGER_VECTORS")), 16, 8000, 30, 10, 10, 0, Duration.ZERO, 0);
        var client = new OpenAiCompatibleEmbeddingClient(properties, chat, JsonMapper.builder().build());
        assertThat(client.configured()).as("AI_EMBEDDING_MODEL and a base URL and key must be configured").isTrue();
        String question = "How many credits do I need to graduate?";
        String answer = "To graduate, a student must complete at least 120 credits.";
        String other = "Tuition must be paid within 14 days after the first day of the semester.";
        long started = System.nanoTime();
        var vectors = client.embedDocuments(List.of(question, answer, other));
        long millis = (System.nanoTime() - started) / 1_000_000;
        assertThat(vectors).hasSize(3);
        for (float[] v : vectors) {
            assertThat(v).hasSize(768);
            assertThat(Math.sqrt(dot(v, v))).isCloseTo(1.0, within(1e-5));
        }
        double toAnswer = dot(vectors.get(0), vectors.get(1));
        double toOther = dot(vectors.get(0), vectors.get(2));
        System.out.printf("LIVE_EMBEDDING model=%s durationMs=%d questionToAnswer=%.4f questionToOther=%.4f answerToOther=%.4f%n",
                client.model(), millis, toAnswer, toOther, dot(vectors.get(1), vectors.get(2)));
        assertThat(toAnswer).isGreaterThan(toOther);
    }

    static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += (double) a[i] * b[i];
        return sum;
    }

    /** Environment variables win over the .env file; values are never printed. */
    public static Map<String, String> settings() throws Exception {
        var values = new HashMap<String, String>();
        Path dotEnv = Path.of(".env");
        if (Files.isRegularFile(dotEnv))
            for (String line : Files.readAllLines(dotEnv)) {
                int eq = line.indexOf('=');
                if (line.isBlank() || line.strip().startsWith("#") || eq < 1) continue;
                values.put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
            }
        System.getenv().forEach((k, v) -> { if (k.startsWith("AI_")) values.put(k, v); });
        return values;
    }
}
