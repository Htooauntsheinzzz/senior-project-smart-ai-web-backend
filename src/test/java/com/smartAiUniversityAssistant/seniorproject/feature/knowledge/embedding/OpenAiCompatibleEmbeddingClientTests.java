package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.embedding;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.EmbeddingException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl.OpenAiCompatibleEmbeddingClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

/** Spec file 50, sections 14.1 and 14.2: a local JDK HTTP stub; no real key or provider is used. */
class OpenAiCompatibleEmbeddingClientTests {
    private static final String KEY = "test-only-embedding-key-91c2";
    private static final String CHAT_KEY = "test-only-chat-key-4b7d";
    private static final String SECRET_TEXT = "Confidential regulation text 7731";
    private final ObjectMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private final BlockingQueue<Recorded> requests = new LinkedBlockingQueue<>();
    private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();
    private ListAppender<ILoggingEvent> logs;

    private record Recorded(String path, Map<String, List<String>> headers, String body) {}
    private record Reply(int status, String body, long delayMillis, Map<String, String> headers) {}

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            byte[] in = exchange.getRequestBody().readAllBytes();
            requests.add(new Recorded(exchange.getRequestURI().getPath(), Map.copyOf(exchange.getRequestHeaders()), new String(in, StandardCharsets.UTF_8)));
            Reply reply = replies.isEmpty() ? new Reply(500, "{}", 0, Map.of()) : replies.poll();
            try {
                if (reply.delayMillis() > 0) Thread.sleep(reply.delayMillis());
                byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
                reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), out.length);
                exchange.getResponseBody().write(out);
            } catch (Exception ignored) {
            } finally {
                exchange.close();
            }
        });
        server.start();
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class)).addAppender(logs);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        ((Logger) LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class)).detachAppender(logs);
        for (ILoggingEvent event : logs.list)
            assertThat(event.getFormattedMessage()).doesNotContain(KEY).doesNotContain(CHAT_KEY).doesNotContain(SECRET_TEXT);
    }

    private String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    static KnowledgeEmbeddingProperties properties(String baseUrl, String key, String model, boolean sendDimensions, int retries, int timeout) {
        return new KnowledgeEmbeddingProperties("", baseUrl, key, model, 768, sendDimensions, true, 16, 8000, timeout, 1, 1, retries,
                Duration.ZERO, 0);
    }

    private OpenAiCompatibleEmbeddingClient client(KnowledgeEmbeddingProperties properties) {
        return new OpenAiCompatibleEmbeddingClient(properties, chat("http://127.0.0.1:1/unused", CHAT_KEY), json);
    }

    private OpenAiCompatibleEmbeddingClient client() { return client(properties(base() + "/v1/", KEY, "embed-model", true, 2, 2)); }

    private static AiAssistantProperties chat(String baseUrl, String key) {
        return new AiAssistantProperties("chat-provider", key, "chat-model", baseUrl, 1, 1, 0.2, 512, 1, Duration.ZERO, 4, 8, 120);
    }

    static double[] raw(int dims, double scale, int hot) {
        double[] v = new double[dims];
        for (int i = 0; i < dims; i++) v[i] = (i == hot ? 3.0 : 0.01 * ((i % 7) - 3)) * scale;
        return v;
    }

    String response(Object... entries) {
        var data = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < entries.length; i += 2)
            data.add(Map.of("object", "embedding", "index", entries[i], "embedding", entries[i + 1]));
        return json.writeValueAsString(Map.of("object", "list", "data", data, "model", "embed-model"));
    }

    void reply(int status, String body) { replies.add(new Reply(status, body, 0, Map.of())); }

    void fails(String code, OpenAiCompatibleEmbeddingClient client) {
        assertThatThrownBy(() -> client.embedDocuments(List.of(SECRET_TEXT)))
                .isInstanceOfSatisfying(EmbeddingException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.getMessage()).doesNotContain("{").doesNotContain(SECRET_TEXT);
                });
    }

    @Test
    void sendsTheRequestSortsByIndexAndNormalizes() throws Exception {
        reply(200, response(1, raw(768, 5, 1), 0, raw(768, 2, 0)));
        var vectors = client().embedDocuments(List.of(SECRET_TEXT, "second ข้อความ"));
        var sent = requests.take();
        assertThat(sent.path()).isEqualTo("/v1/embeddings");
        assertThat(sent.headers().get("Authorization")).containsExactly("Bearer " + KEY);
        JsonNode body = json.readTree(sent.body());
        assertThat(body.get("model").asString()).isEqualTo("embed-model");
        assertThat(body.get("input").isArray()).isTrue();
        assertThat(body.get("input").get(1).asString()).isEqualTo("second ข้อความ");
        assertThat(body.get("encoding_format").asString()).isEqualTo("float");
        assertThat(body.get("dimensions").asInt()).isEqualTo(768);
        assertThat(vectors).hasSize(2);
        for (float[] v : vectors) {
            assertThat(v).hasSize(768);
            double norm = Math.sqrt(IntStream.range(0, 768).mapToDouble(i -> (double) v[i] * v[i]).sum());
            assertThat(norm).isCloseTo(1.0, within(1e-5));
        }
        assertThat(vectors.get(0)[0]).isGreaterThan(0.9f);   // index 0 came second in the response
        assertThat(vectors.get(1)[1]).isGreaterThan(0.9f);

        reply(200, response(0, raw(768, 1, 0)));
        client(properties(base(), KEY, "embed-model", false, 0, 2)).embedDocuments(List.of("one"));
        assertThat(json.readTree(requests.take().body()).has("dimensions")).isFalse();
        assertThat(client().dimensions()).isEqualTo(768);
        assertThat(client().model()).isEqualTo("embed-model");
    }

    @Test
    void emptyBaseUrlAndKeyFallBackToTheChatValues() throws Exception {
        reply(200, response(0, raw(768, 1, 0)));
        var fallback = new OpenAiCompatibleEmbeddingClient(properties("", "", "embed-model", true, 0, 2), chat(base() + "/chat-base", CHAT_KEY), json);
        assertThat(fallback.configured()).isTrue();
        fallback.embedDocuments(List.of("x"));
        var sent = requests.take();
        assertThat(sent.path()).isEqualTo("/chat-base/embeddings");
        assertThat(sent.headers().get("Authorization")).containsExactly("Bearer " + CHAT_KEY);
    }

    @Test
    void invalidResponses() {
        var client = client(properties(base(), KEY, "m", true, 0, 2));
        List<String> invalid = List.of(
                response(0, raw(768, 1, 0), 1, raw(768, 1, 1)),        // two entries for one text
                json.writeValueAsString(Map.of("object", "list")),     // no data
                "{\"data\":[{\"index\":0,\"embedding\":[\"a\"" + ",0".repeat(767) + "]}]}",
                "{\"data\":[{\"index\":0,\"embedding\":[NaN" + ",0".repeat(767) + "]}]}",
                response(0, new double[768]),                          // all zeros
                "not json",
                "{\"data\":[{\"index\":5,\"embedding\":[1" + ",0".repeat(767) + "]}]}");
        for (String body : invalid) {
            reply(200, body);
            fails("EMBEDDING_INVALID_RESPONSE", client);
        }
        reply(200, response(0, raw(768, 1, 0), 0, raw(768, 1, 0)));
        assertThatThrownBy(() -> client.embedDocuments(List.of("a", "b"))).isInstanceOfSatisfying(EmbeddingException.class,
                e -> assertThat(e.code()).isEqualTo("EMBEDDING_INVALID_RESPONSE"));
        reply(200, "{\"data\":[{\"index\":0,\"embedding\":[" + "0.5,".repeat(2_300_000) + "1]}]}");
        fails("EMBEDDING_INVALID_RESPONSE", client);
        reply(200, response(0, raw(767, 1, 0)));
        fails("EMBEDDING_DIMENSION_MISMATCH", client);
        reply(200, response(0, raw(3072, 1, 0)));
        var strict = client(new KnowledgeEmbeddingProperties("", base(), KEY, "m", 768, true, false, 16, 8000, 2, 1, 1, 0,
                Duration.ZERO, 0));
        fails("EMBEDDING_DIMENSION_MISMATCH", strict);
    }

    @Test
    void missingIndexMeansArrayPosition() {
        // Real gemini-embedding-001 reply shape: the first entry has no "index" (0 is omitted), the others have one.
        var client = client(properties(base(), KEY, "gemini-embedding-001", true, 0, 2));
        var first = Map.<String, Object>of("object", "embedding", "embedding", raw(768, 1, 0));
        var second = Map.<String, Object>of("object", "embedding", "index", 1, "embedding", raw(768, 1, 1));
        reply(200, json.writeValueAsString(Map.of("object", "list", "data", List.of(first, second))));
        var vectors = client.embedDocuments(List.of("a", "b"));
        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)[0]).isGreaterThan(0.9f);
        assertThat(vectors.get(1)[1]).isGreaterThan(0.9f);
        // A missing index that collides with a stated one is still rejected.
        var stated = Map.<String, Object>of("object", "embedding", "index", 1, "embedding", raw(768, 1, 0));
        reply(200, json.writeValueAsString(Map.of("object", "list", "data", List.of(first, first, stated))));
        assertThatThrownBy(() -> client.embedDocuments(List.of("a", "b", "c"))).isInstanceOfSatisfying(EmbeddingException.class,
                e -> assertThat(e.code()).isEqualTo("EMBEDDING_INVALID_RESPONSE"));
    }

    @Test
    void longerVectorIsTruncatedAndNormalized() {
        // gemini-embedding-001 returns 3072 numbers when the dimensions field is not honoured.
        var client = client(properties(base(), KEY, "gemini-embedding-001", true, 0, 2));
        double[] full = raw(3072, 1, 0);
        full[3000] = 50;                                         // only in the cut part: must not affect the result
        reply(200, response(0, full));
        float[] vector = client.embedDocuments(List.of("x")).get(0);
        assertThat(vector).hasSize(768);
        double sum = 0;
        for (float v : vector) sum += v * v;
        assertThat(Math.sqrt(sum)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
        double[] head = java.util.Arrays.copyOf(full, 768);
        double norm = Math.sqrt(java.util.Arrays.stream(head).map(v -> v * v).sum());
        assertThat((double) vector[0]).isCloseTo(head[0] / norm, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void statusMappingAndRetries() {
        var client = client(properties(base(), KEY, "m", true, 2, 2));
        for (int status : List.of(401, 403)) {
            requests.clear();
            reply(status, "{\"error\":\"secret provider text\"}");
            fails("EMBEDDING_AUTHENTICATION_FAILED", client);
            assertThat(requests).hasSize(1);
        }
        for (int status : List.of(400, 404)) {
            requests.clear();
            reply(status, "{}");
            fails("EMBEDDING_REQUEST_REJECTED", client);
            assertThat(requests).hasSize(1);
        }
        requests.clear();
        for (int i = 0; i < 3; i++) reply(429, "{}");
        assertThatThrownBy(() -> client.embedDocuments(List.of("x"))).isInstanceOfSatisfying(EmbeddingException.class, e -> {
            assertThat(e.code()).isEqualTo("EMBEDDING_RATE_LIMITED");
            assertThat(e.retryable()).isTrue();
        });
        assertThat(requests).hasSize(3);
        requests.clear();
        reply(429, "{}");
        reply(200, response(0, raw(768, 1, 0)));
        assertThat(client.embedDocuments(List.of("x"))).hasSize(1);
        assertThat(requests).hasSize(2);
        requests.clear();
        for (int i = 0; i < 3; i++) reply(500, "{}");
        fails("EMBEDDING_PROVIDER_UNAVAILABLE", client);
        assertThat(requests).hasSize(3);
        replies.add(new Reply(302, "{}", 0, Map.of("Location", base() + "/elsewhere")));
        requests.clear();
        fails("EMBEDDING_REQUEST_REJECTED", client);
        assertThat(requests).hasSize(1);
    }

    @Test
    void refusedConnectionTimeoutAndNotConfigured() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var refused = client(properties("http://127.0.0.1:" + port, KEY, "m", true, 1, 2));
        long started = System.nanoTime();
        fails("EMBEDDING_PROVIDER_UNAVAILABLE", refused);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));

        replies.add(new Reply(200, response(0, raw(768, 1, 0)), 3000, Map.of()));
        requests.clear();
        fails("EMBEDDING_TIMEOUT", client(properties(base(), KEY, "m", true, 2, 1)));
        assertThat(requests).hasSize(1);

        requests.clear();
        var unconfigured = client(properties(base(), KEY, "", true, 2, 1));
        assertThat(unconfigured.configured()).isFalse();
        fails("EMBEDDING_NOT_CONFIGURED", unconfigured);
        assertThat(requests).isEmpty();
        assertThatThrownBy(() -> client().embedDocuments(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client().embedDocuments(List.of(" "))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client().embedDocuments(Collections.nCopies(17, "x"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void embedQueryUsesTheQueryTimeoutAndOneRetry() {
        // document timeout 5 s, query timeout 1 s: a 2.5 s answer is a timeout only for the query
        var props = new KnowledgeEmbeddingProperties("", base(), KEY, "m", 768, true, true, 16, 8000, 5, 1, 1, 3, Duration.ZERO, 0);
        var client = client(props);
        replies.add(new Reply(200, response(0, raw(768, 1, 0)), 2500, Map.of()));
        assertThatThrownBy(() -> client.embedQuery("question")).isInstanceOfSatisfying(EmbeddingException.class,
                e -> assertThat(e.code()).isEqualTo("EMBEDDING_TIMEOUT"));
        replies.clear();
        requests.clear();
        for (int i = 0; i < 4; i++) reply(503, "{}");
        assertThatThrownBy(() -> client.embedQuery("question")).isInstanceOf(EmbeddingException.class);
        assertThat(requests).hasSize(2);
        replies.clear();
        replies.add(new Reply(200, response(0, raw(768, 1, 0)), 2500, Map.of()));
        assertThat(client.embedDocuments(List.of("document"))).hasSize(1);
    }

    @Test
    void propertiesValidationAndMaskedKey() {
        var validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        var wrongDims = new KnowledgeEmbeddingProperties("", "", "k", "m", 1024, true, true, 16, 8000, 30, 10, 10, 2, Duration.ofSeconds(2), 1000);
        assertThat(validator.validate(wrongDims)).isNotEmpty();
        var unsafe = new KnowledgeEmbeddingProperties("", "ftp://user:pw@host/x?y", "k", "m", 768, true, true, 16, 8000, 30, 10, 10, 2, Duration.ofSeconds(2), 1000);
        assertThat(validator.validate(unsafe)).isNotEmpty();
        var good = new KnowledgeEmbeddingProperties("", "https://example.test/v1", KEY, "m", 768, true, true, 16, 8000, 30, 10, 10, 2, Duration.ofSeconds(2), 1000);
        assertThat(validator.validate(good)).isEmpty();
        assertThat(good.toString()).doesNotContain(KEY).contains("****");
    }
}
