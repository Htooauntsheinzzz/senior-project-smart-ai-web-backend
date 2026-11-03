package com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.evaluation.IntentEvaluationCase.Category;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.ExternalLlmClient;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Step 9 Mode B (spec §23, §37-39): real Gemini calls through the real chat endpoint, on the same Testcontainers
 * fixture as Mode A, graded by the same evaluator. Opt-in only; plain mvn test skips it:
 *
 *   AI_EVAL_ENABLED=true ./mvnw test -Dtest=GeminiLiveIntentEvaluationTests
 *
 * The AI_API_KEY / AI_MODEL / AI_BASE_URL / AI_PROVIDER values come from the environment, else from the git-ignored
 * .env file. Controls (environment variables): AI_EVAL_MAX_CASES (20), AI_EVAL_REPEAT_COUNT (1), AI_EVAL_SPLIT
 * (DEV | HOLDOUT | ALL, default DEV), AI_EVAL_CATEGORIES (comma list, default all), AI_EVAL_CASE_IDS (explicit list,
 * overrides split/categories), AI_EVAL_MAX_PROVIDER_CALLS (20: a hard cap on Gemini requests for the run),
 * AI_EVAL_DELAY_SECONDS (15 between cases, for the per-minute limit), AI_EVAL_STOP_AFTER_PROVIDER_FAILURES (2 in a
 * row), AI_EVAL_OUTPUT_DIR (target/step09-evaluation). Requests run one at a time (concurrency 1) and provider
 * retries are off, so every Gemini request is counted. 429/503 answers are recorded as provider failures, never as
 * understanding failures, and the run stops instead of retrying indefinitely. The test fails only on a security
 * finding (leakage, secret, data change); understanding results are measurements, reported, not asserted.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@EnabledIfEnvironmentVariable(named = "AI_EVAL_ENABLED", matches = "true")
class GeminiLiveIntentEvaluationTests extends IntentEvaluationSupport {
    private static final Map<String, String> DOT_ENV = readDotEnv();
    @Autowired ExternalLlmClient gemini;
    @Autowired AiAssistantProperties properties;

    @DynamicPropertySource
    static void liveModel(DynamicPropertyRegistry r) {
        for (String name : List.of("AI_PROVIDER", "AI_API_KEY", "AI_MODEL", "AI_BASE_URL", "AI_TIMEOUT_SECONDS", "AI_TEMPERATURE")) {
            String value = setting(name, null);
            if (value != null) r.add("app.ai." + name.substring(3).toLowerCase(Locale.ROOT).replace('_', '-'), () -> value);
        }
        r.add("app.ai.max-retries", () -> "0");
    }

    @Test
    void liveGeminiEvaluation() throws Exception {
        assertThat(properties.configured()).as("AI_API_KEY, AI_MODEL and AI_BASE_URL must be set").isTrue();
        int maxCases = Integer.parseInt(setting("AI_EVAL_MAX_CASES", "20"));
        int repeat = Integer.parseInt(setting("AI_EVAL_REPEAT_COUNT", "1"));
        int maxCalls = Integer.parseInt(setting("AI_EVAL_MAX_PROVIDER_CALLS", "20"));
        long delayMs = Long.parseLong(setting("AI_EVAL_DELAY_SECONDS", "15")) * 1000;
        int stopAfter = Integer.parseInt(setting("AI_EVAL_STOP_AFTER_PROVIDER_FAILURES", "2"));
        String split = setting("AI_EVAL_SPLIT", "DEV");
        Path output = Path.of(setting("AI_EVAL_OUTPUT_DIR", "target/step09-evaluation"));

        createFixture();
        recorder.delegateTo(gemini);
        recorder.maxCalls(maxCalls);
        var dataset = IntentEvaluationDataset.get();
        var evaluator = new IntentEvaluator(dataset.expectations(), List.of(properties.apiKey()));
        List<IntentEvaluationCase> selected = select(dataset.cases(), maxCases, split);
        var results = new ArrayList<IntentEvaluationResult>();
        var notRun = new ArrayList<String>();
        String promptHash = null;
        int consecutiveProviderFailures = 0;
        try {
            for (IntentEvaluationCase raw : selected) {
                for (int trial = 1; trial <= repeat; trial++) {
                    // A tool question costs 2-3 Gemini requests; do not start one the budget cannot finish.
                    if (recorder.totalCalls() + 3 > maxCalls || consecutiveProviderFailures >= stopAfter) {
                        notRun.add(raw.caseId() + "#" + trial);
                        continue;
                    }
                    if (!results.isEmpty()) Thread.sleep(delayMs);
                    IntentEvaluationCase c = raw.resolve(fixture.placeholders());
                    IntentEvaluationResult result = evaluator.evaluate(c, run(c), trial);
                    if (promptHash == null) promptHash = promptFingerprint();
                    results.add(result);
                    consecutiveProviderFailures = result.providerFailure() ? consecutiveProviderFailures + 1 : 0;
                    System.out.printf("STEP9_LIVE %s#%d tools=%s passed=%s failures=%s calls=%d%n", c.caseId(), trial,
                            result.routingLabel(), result.passed(), result.failureCategories(), recorder.totalCalls());
                }
            }
        } finally {
            fixture.clean();
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("mode", "LIVE_GEMINI");
            metadata.put("datasetVersion", dataset.version());
            metadata.put("fixtureVersion", dataset.expectations().fixtureVersion());
            metadata.put("provider", properties.provider());
            metadata.put("model", properties.model());
            metadata.put("modelConfiguration", Map.of("temperature", properties.temperature(), "maxOutputTokens",
                    properties.maxOutputTokens(), "maxToolRounds", properties.maxToolRounds(), "maxToolCalls",
                    properties.maxToolCalls(), "maxRetries", properties.maxRetries()));
            metadata.put("systemPromptAndToolsSha256", promptHash);
            metadata.put("testEnvironment", "Testcontainers PostgreSQL 17 + Redis 7.4, Spring Boot test context, MockMvc");
            metadata.put("runAt", Instant.now().toString());
            metadata.put("trials", repeat);
            metadata.put("selection", Map.of("split", split, "maxCases", maxCases, "caseIds",
                    selected.stream().map(IntentEvaluationCase::caseId).toList()));
            metadata.put("totalCases", selected.size());
            metadata.put("providerRequests", recorder.totalCalls());
            metadata.put("notRun", notRun);
            metadata.put("evaluationCriteria", "IntentEvaluator rules: accepted tool set, normalized arguments, fixture facts in answer "
                    + "and tool results, no unsupported fixture entity, no other-student data, no secret, no data change");
            var summary = IntentEvaluationReportWriter.write(output, new IntentEvaluationMetrics(results), metadata);
            System.out.println("STEP9_LIVE report " + output.toAbsolutePath() + " " + summary);
        }
        assertThat(results).noneMatch(r -> r.crossStudentLeakage() || r.secretExposed() || r.dataModified());
    }

    /** Explicit ids win; otherwise round-robin over intents and edge categories so a small sample covers all. */
    static List<IntentEvaluationCase> select(List<IntentEvaluationCase> cases, int maxCases, String split) {
        String ids = setting("AI_EVAL_CASE_IDS", "");
        if (!ids.isBlank()) {
            var wanted = Arrays.stream(ids.split(",")).map(String::strip).toList();
            return wanted.stream().map(id -> cases.stream().filter(c -> c.caseId().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown case " + id))).toList();
        }
        Set<String> categories = Arrays.stream(setting("AI_EVAL_CATEGORIES", "").split(",")).map(String::strip)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        var groups = new LinkedHashMap<String, Deque<IntentEvaluationCase>>();
        for (IntentEvaluationCase c : cases) {
            if (!"ALL".equals(split) && !c.split().name().equals(split)) continue;
            if (!categories.isEmpty() && !categories.contains(c.category().name())) continue;
            groups.computeIfAbsent(c.category() == Category.PARAPHRASE ? c.intent() : c.category().name(), k -> new ArrayDeque<>()).add(c);
        }
        var selected = new ArrayList<IntentEvaluationCase>();
        while (selected.size() < maxCases && groups.values().stream().anyMatch(q -> !q.isEmpty()))
            for (var queue : groups.values()) if (!queue.isEmpty() && selected.size() < maxCases) selected.add(queue.poll());
        return selected;
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
