package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.*;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception.EmbeddingException;
import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.service.EmbeddingClient;
import java.io.*;
import java.net.SocketTimeoutException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.slf4j.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;

/**
 * Embeddings through an OpenAI-compatible POST {base-url}/embeddings (spec file 50, section 7), with the same safety
 * pattern as the chat client: JDK HttpClient, redirects never followed, connect and read timeouts always set, a bounded
 * response body, only HTTP 429, 5xx and connection failures retried. Responses are validated (count, indexes, size,
 * finite non-zero values) and every vector is L2-normalized. A vector longer than the configured size is cut to its
 * first {@code dimensions} numbers when truncate-longer-vectors is on (Matryoshka models such as gemini-embedding-001). Logs carry status, text count and duration only.
 */
@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);
    static final int MAX_RESPONSE_BYTES = 8 * 1024 * 1024;

    private final KnowledgeEmbeddingProperties properties;
    private final AiAssistantProperties chat;
    private final ObjectMapper json;
    private final RestClient documents;
    private final RestClient queries;

    public OpenAiCompatibleEmbeddingClient(KnowledgeEmbeddingProperties properties, AiAssistantProperties chat, ObjectMapper json) {
        this.properties = properties;
        this.chat = chat;
        this.json = json;
        this.documents = client(properties.timeoutSeconds());
        this.queries = client(properties.queryTimeoutSeconds());
    }

    private RestClient client(int readTimeoutSeconds) {
        var http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override public boolean configured() { return properties.configured(chat); }
    @Override public String model() { return properties.model() == null ? null : properties.model().strip(); }
    @Override public int dimensions() { return properties.dimensions(); }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        if (texts == null || texts.isEmpty()) throw new IllegalArgumentException("No texts to embed");
        if (texts.size() > properties.batchSize()) throw new IllegalArgumentException("More texts than batch-size");
        if (texts.stream().anyMatch(t -> t == null || t.isBlank())) throw new IllegalArgumentException("Blank text");
        return embed(texts, documents, properties.maxRetries());
    }

    @Override
    public float[] embedQuery(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Blank text");
        return embed(List.of(text), queries, Math.min(1, properties.maxRetries())).get(0);
    }

    private List<float[]> embed(List<String> texts, RestClient http, int maxRetries) {
        if (!configured()) throw new EmbeddingException("EMBEDDING_NOT_CONFIGURED");
        byte[] body = requestBody(texts);
        for (int attempt = 0; ; attempt++) {
            try {
                return send(http, body, texts.size());
            } catch (Retryable e) {
                if (attempt >= maxRetries) throw e.failure;
                pause(attempt + 1);
            }
        }
    }

    /** A failure the client may retry (429, 5xx, connection failure). */
    private static final class Retryable extends RuntimeException {
        final EmbeddingException failure;
        Retryable(String code) {
            super(code, null, false, false);
            this.failure = new EmbeddingException(code);
        }
    }

    private record RawResponse(int status, byte[] body) {}

    private List<float[]> send(RestClient http, byte[] body, int count) {
        long started = System.nanoTime();
        RawResponse raw;
        try {
            raw = http.post().uri(endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.effectiveApiKey(chat))
                    .body(body)
                    .exchange((req, res) -> new RawResponse(res.getStatusCode().value(), read(res.getBody())), true);
        } catch (RuntimeException e) {
            if (isReadTimeout(e)) {
                log.warn("EMBEDDING_PROVIDER status=timeout texts={} durationMs={}", count, millis(started));
                throw new EmbeddingException("EMBEDDING_TIMEOUT");
            }
            // Exception messages can echo the URL, so only the type is logged.
            log.warn("EMBEDDING_PROVIDER status=network texts={} durationMs={} error={}", count, millis(started), rootCause(e).getClass().getSimpleName());
            throw new Retryable("EMBEDDING_PROVIDER_UNAVAILABLE");
        }
        int status = raw.status();
        log.info("EMBEDDING_PROVIDER status={} texts={} durationMs={}", status, count, millis(started));
        if (status == 429) throw new Retryable("EMBEDDING_RATE_LIMITED");
        if (status >= 500) throw new Retryable("EMBEDDING_PROVIDER_UNAVAILABLE");
        if (status == 401 || status == 403) throw new EmbeddingException("EMBEDDING_AUTHENTICATION_FAILED");
        if (status < 200 || status >= 300) throw new EmbeddingException("EMBEDDING_REQUEST_REJECTED");
        if (raw.body() == null) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
        return parse(raw.body(), count);
    }

    /** Reads at most the limit plus one byte; null means the body was too large. */
    private static byte[] read(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
        return bytes.length > MAX_RESPONSE_BYTES ? null : bytes;
    }

    List<float[]> parse(byte[] body, int count) {
        JsonNode root;
        try {
            root = json.readTree(new String(body, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
        }
        JsonNode data = root == null ? null : root.get("data");
        if (data == null || !data.isArray() || data.size() != count) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
        JsonNode[] byIndex = new JsonNode[count];
        int position = 0;
        for (JsonNode entry : data) {
            // Gemini's OpenAI-compatible endpoint leaves "index" out when it is 0, so a missing index means the entry's
            // position in the array. A present index must still be a valid, unused integer.
            JsonNode index = entry.get("index");
            int i = position++;
            if (index != null && !index.isNull()) {
                if (!index.isIntegralNumber() || !index.canConvertToInt()) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
                i = index.asInt();
            }
            if (i < 0 || i >= count || byIndex[i] != null) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
            byIndex[i] = entry.get("embedding");
        }
        var vectors = new ArrayList<float[]>(count);
        for (JsonNode embedding : byIndex) {
            if (embedding == null || !embedding.isArray()) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
            int size = embedding.size(), dims = properties.dimensions();
            if (size < dims || (size > dims && !properties.truncateLongerVectors()))
                throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
            double[] values = new double[dims];
            double sum = 0;
            for (int i = 0; i < values.length; i++) {
                JsonNode value = embedding.get(i);
                if (value == null || !value.isNumber()) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
                values[i] = value.asDouble();
                if (!Double.isFinite(values[i])) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
                sum += values[i] * values[i];
            }
            double norm = Math.sqrt(sum);
            if (norm == 0 || !Double.isFinite(norm)) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
            float[] vector = new float[values.length];
            for (int i = 0; i < values.length; i++) vector[i] = (float) (values[i] / norm);
            vectors.add(vector);
        }
        return vectors;
    }

    private byte[] requestBody(List<String> texts) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("model", model());
        payload.put("input", texts);
        payload.put("encoding_format", "float");
        if (properties.sendDimensions()) payload.put("dimensions", properties.dimensions());
        return json.writeValueAsBytes(payload);
    }

    private String endpoint() {
        String base = properties.effectiveBaseUrl(chat);
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/embeddings";
    }

    /** Waits retry-backoff × n; ends at once when the thread is interrupted (the interrupt flag stays set). */
    private void pause(int retry) {
        try {
            Thread.sleep(properties.retryBackoff().multipliedBy(retry));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("EMBEDDING_PROVIDER_UNAVAILABLE");
        }
    }

    private static boolean isReadTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpConnectTimeoutException) return false;
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) return true;
        }
        return false;
    }

    private static Throwable rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t;
    }

    private static long millis(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
