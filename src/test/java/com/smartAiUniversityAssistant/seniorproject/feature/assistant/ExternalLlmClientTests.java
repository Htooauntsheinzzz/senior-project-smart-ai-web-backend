package com.smartAiUniversityAssistant.seniorproject.feature.assistant;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl.ExternalLlmClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

/** Provider contract tests against a local JDK HTTP stub; no real key or network access is used. */
class ExternalLlmClientTests {
    private static final String KEY = "test-only-not-a-real-key-7f3a";
    private static final String SYSTEM = "trusted system prompt";
    private final ObjectMapper json = JsonMapper.builder().build();
    private HttpServer server;
    private final BlockingQueue<Recorded> requests = new LinkedBlockingQueue<>();
    private final Deque<Reply> replies = new ConcurrentLinkedDeque<>();

    private record Recorded(String method, String path, Map<String, List<String>> headers, String body) {}
    private record Reply(int status, String body, long delayMillis) {}

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            byte[] in = exchange.getRequestBody().readAllBytes();
            requests.add(new Recorded(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    Map.copyOf(exchange.getRequestHeaders()), new String(in, StandardCharsets.UTF_8)));
            Reply reply = replies.isEmpty() ? new Reply(500, "{}", 0) : replies.poll();
            try {
                if (reply.delayMillis() > 0) Thread.sleep(reply.delayMillis());
                byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(reply.status(), out.length);
                exchange.getResponseBody().write(out);
            } catch (Exception ignored) {
                // The client may already have given up (timeout test).
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void sendsOpenAiCompatibleRequestAndParsesTheAnswer() throws Exception {
        reply(200, success("สวัสดีครับ မင်္ဂလာပါ Hello", "provider-model-v2"));
        String message = "อธิบายหน่วยกิต / ခရက်ဒစ်ဆိုတာဘာလဲ?";

        LlmResponse response = client(baseUrl() + "/v1/").chat(new LlmRequest(SYSTEM, message, "req-1"));

        assertThat(response.content()).isEqualTo("สวัสดีครับ မင်္ဂလာပါ Hello");
        assertThat(response.model()).isEqualTo("provider-model-v2");
        assertThat(response.providerRequestId()).isEqualTo("chatcmpl-123");
        // Token counts come straight from the provider's usage block.
        assertThat(response.usage()).isEqualTo(new LlmUsage(10, 5, 15));
        Recorded sent = requests.take();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/v1/chat/completions");
        assertThat(header(sent, "Authorization")).isEqualTo("Bearer " + KEY);
        assertThat(header(sent, "Content-Type")).startsWith("application/json");
        JsonNode body = json.readTree(sent.body());
        assertThat(body.get("model").stringValue()).isEqualTo("configured-model");
        assertThat(body.get("temperature").asDouble()).isEqualTo(0.2);
        assertThat(body.get("max_tokens").asInt()).isEqualTo(512);
        JsonNode messages = body.get("messages");
        assertThat(messages.size()).isEqualTo(2);
        assertThat(messages.get(0).get("role").stringValue()).isEqualTo("system");
        assertThat(messages.get(0).get("content").stringValue()).isEqualTo(SYSTEM);
        assertThat(messages.get(1).get("role").stringValue()).isEqualTo("user");
        // UTF-8 Thai and Myanmar text reaches the provider unchanged.
        assertThat(messages.get(1).get("content").stringValue()).isEqualTo(message);
        // Only the provider's own fields are sent: no request id, student data or JWT.
        assertThat(iterable(body.propertyNames())).containsExactlyInAnyOrder("model", "messages", "temperature", "max_tokens");
        assertThat(sent.headers().keySet()).noneMatch(name -> name.equalsIgnoreCase("Cookie"));
    }

    @Test
    void fallsBackToConfiguredModelWhenProviderOmitsIt() {
        reply(200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hi\"}}]}");
        LlmResponse response = client(baseUrl()).chat(request());
        assertThat(response.model()).isEqualTo("configured-model");
        assertThat(response.providerRequestId()).isNull();
        assertThat(response.usage()).isNull();
    }

    @Test
    void usageIsParsedLenientlyAndNeverFailsTheAnswer() {
        // Gemini's total includes thinking tokens, so it may exceed prompt + completion.
        reply(200, "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}],\"usage\":{\"prompt_tokens\":160,\"completion_tokens\":49,\"total_tokens\":369}}");
        assertThat(client(baseUrl()).chat(request()).usage()).isEqualTo(new LlmUsage(160, 49, 369));
        // A partial block counts what is there; a malformed one is "not reported".
        reply(200, "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}],\"usage\":{\"total_tokens\":7,\"prompt_tokens\":\"x\",\"completion_tokens\":-3}}");
        assertThat(client(baseUrl()).chat(request()).usage()).isEqualTo(new LlmUsage(0, 0, 7));
        reply(200, "{\"choices\":[{\"message\":{\"content\":\"Hi\"}}],\"usage\":\"n/a\"}");
        assertThat(client(baseUrl()).chat(request()).usage()).isNull();
    }

    @Test
    void providerAuthenticationFailureIsUnavailableAndNotRetried() {
        reply(401, "{\"error\":{\"message\":\"Incorrect API key provided: " + KEY + "\"}}");
        assertFailure(client(baseUrl()), 503, "AI_ASSISTANT_UNAVAILABLE");
        assertThat(requests).hasSize(1);
    }

    @Test
    void otherClientErrorsAreUnavailableAndNotRetried() {
        reply(400, "{\"error\":{\"message\":\"model not found\"}}");
        assertFailure(client(baseUrl()), 503, "AI_ASSISTANT_UNAVAILABLE");
        reply(403, "{}");
        assertFailure(client(baseUrl()), 503, "AI_ASSISTANT_UNAVAILABLE");
        assertThat(requests).hasSize(2);
    }

    @Test
    void rateLimitIsRetriedOnceThenReported() {
        reply(429, "{\"error\":\"slow down\"}");
        reply(429, "{\"error\":\"slow down\"}");
        reply(200, success("never reached", "m"));
        assertFailure(client(baseUrl()), 429, "AI_PROVIDER_RATE_LIMITED");
        assertThat(requests).hasSize(2);
    }

    @Test
    void transientFailureRecoversOnTheSingleRetry() {
        reply(429, "{}");
        reply(200, success("Recovered", "m"));
        assertThat(client(baseUrl()).chat(request()).content()).isEqualTo("Recovered");
        assertThat(requests).hasSize(2);
    }

    @Test
    void serverErrorIsRetriedOnceThenUnavailable() {
        reply(500, "{\"error\":\"internal stack trace at provider.Foo\"}");
        reply(503, "{}");
        assertFailure(client(baseUrl()), 503, "AI_ASSISTANT_UNAVAILABLE");
        assertThat(requests).hasSize(2);
    }

    @Test
    void retriesCanBeDisabled() {
        reply(500, "{}");
        assertFailure(client(baseUrl(), 0), 503, "AI_ASSISTANT_UNAVAILABLE");
        assertThat(requests).hasSize(1);
    }

    @Test
    void slowProviderTimesOutWithoutRetry() {
        reply(200, success("too late", "m"), 2500);
        long started = System.nanoTime();
        assertFailure(client(baseUrl()), 503, "AI_REQUEST_TIMEOUT");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(2400));
        assertThat(requests).hasSize(1);
    }

    @Test
    void malformedOrIncompleteResponsesAreInvalid() {
        for (String body : List.of("not json at all", "{\"choices\":[]}", "{\"unexpected\":true}",
                "{\"choices\":[{\"message\":{\"content\":\"   \"}}]}",
                "{\"choices\":[{\"message\":{\"content\":null}}]}",
                "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\"}]}}]}", "")) {
            reply(200, body);
            assertFailure(client(baseUrl()), 502, "INVALID_AI_PROVIDER_RESPONSE");
        }
        // Invalid responses are not retried.
        assertThat(requests).hasSize(7);
    }

    @Test
    void unreachableProviderIsUnavailable() throws IOException {
        int closedPort;
        try (var socket = new ServerSocket(0)) { closedPort = socket.getLocalPort(); }
        assertFailure(client("http://127.0.0.1:" + closedPort), 503, "AI_ASSISTANT_UNAVAILABLE");
    }

    @Test
    void missingConfigurationFailsWithoutCallingTheProvider() {
        for (var properties : List.of(properties(baseUrl(), "", "configured-model", 1),
                properties(baseUrl(), KEY, " ", 1), properties("", KEY, "configured-model", 1))) {
            assertThatThrownBy(() -> new ExternalLlmClient(properties, json).chat(request()))
                    .isInstanceOfSatisfying(AiAssistantFailure.class, e -> assertThat(e.code()).isEqualTo("AI_ASSISTANT_UNAVAILABLE"));
        }
        assertThat(requests).isEmpty();
    }

    @Test
    void apiKeyNeverAppearsInPropertiesOrErrors() {
        var properties = properties(baseUrl(), KEY, "configured-model", 1);
        assertThat(properties.toString()).doesNotContain(KEY).contains("apiKey=****");
        assertThat(properties(baseUrl(), "", "m", 1).toString()).contains("apiKey=<unset>");
        reply(401, "{\"error\":\"Incorrect API key provided: " + KEY + "\"}");
        assertThatThrownBy(() -> client(baseUrl()).chat(request()))
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(KEY).doesNotContain("Incorrect");
                    assertThat(e.getCause()).isNull();
                });
    }

    // ---- Step 6: function calling over the OpenAI-compatible format ----

    private static final String TOOL_TURN = "{\"id\":\"resp-1\",\"model\":\"gemini-test\",\"choices\":[{\"finish_reason\":\"tool_calls\","
            + "\"message\":{\"role\":\"assistant\",\"tool_calls\":["
            + "{\"extra_content\":{\"google\":{\"thought_signature\":\"sig-abc\"}},\"id\":\"call_1\",\"type\":\"function\","
            + "\"function\":{\"name\":\"get_my_course_lecturer\",\"arguments\":\"{\\\"courseIdentifier\\\":\\\"CS301\\\"}\"}},"
            + "{\"id\":\"call_2\",\"type\":\"function\",\"function\":{\"name\":\"get_section_availability\","
            + "\"arguments\":\"{\\\"courseSectionId\\\":15}\"}}]}}]}";

    private static LlmRequest toolRequest(List<LlmToolRound> rounds) {
        var tool = new AcademicAiToolDefinition("get_my_enrollments", "List enrollments",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));
        return new LlmRequest(SYSTEM, "What courses am I enrolled in?", "req", List.of(tool), rounds);
    }

    @Test
    void sendsApprovedToolDeclarations() throws Exception {
        reply(200, success("Answer", "m"));
        client(baseUrl()).chat(toolRequest(List.of()));
        JsonNode body = json.readTree(requests.take().body());
        JsonNode tool = body.get("tools").get(0);
        assertThat(body.get("tools").size()).isEqualTo(1);
        assertThat(tool.get("type").stringValue()).isEqualTo("function");
        assertThat(tool.get("function").get("name").stringValue()).isEqualTo("get_my_enrollments");
        assertThat(tool.get("function").get("description").stringValue()).isEqualTo("List enrollments");
        assertThat(tool.get("function").get("parameters").get("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void requiresAToolOnlyForTheGroundingRetry() throws Exception {
        reply(200, TOOL_TURN);
        reply(200, TOOL_TURN);
        reply(200, success("Answer", "m"));
        ExternalLlmClient client = client(baseUrl());
        LlmRequest plain = toolRequest(List.of());
        client.chat(plain);
        assertThat(json.readTree(requests.take().body()).has("tool_choice")).isFalse();

        client.chat(new LlmRequest(plain.systemPrompt(), plain.userMessage(), plain.requestId(), plain.tools(), List.of(), true));
        JsonNode body = json.readTree(requests.take().body());
        assertThat(body.get("tool_choice").stringValue()).isEqualTo("required");
        assertThat(body.get("tools").size()).isEqualTo(1);

        // Without declared tools nothing can be required, so the flag is ignored.
        client.chat(new LlmRequest(SYSTEM, "What is a credit hour?", "req", List.of(), List.of(), true));
        assertThat(json.readTree(requests.take().body()).has("tool_choice")).isFalse();
    }

    @Test
    void parsesMultipleToolCallsWithIdsAndArguments() {
        reply(200, TOOL_TURN);
        LlmResponse response = client(baseUrl()).chat(toolRequest(List.of()));
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.content()).isNull();
        assertThat(response.model()).isEqualTo("gemini-test");
        assertThat(response.toolCalls()).hasSize(2);
        assertThat(response.toolCalls().get(0)).isEqualTo(new LlmToolCall("call_1", "get_my_course_lecturer", Map.of("courseIdentifier", "CS301")));
        assertThat(response.toolCalls().get(1).callId()).isEqualTo("call_2");
        assertThat(((Number) response.toolCalls().get(1).arguments().get("courseSectionId")).longValue()).isEqualTo(15L);
    }

    @Test
    void conversationHistoryIsSentAsPlainTurnsBetweenSystemAndTheCurrentQuestion() throws Exception {
        reply(200, TOOL_TURN);
        reply(200, success("CS301 is in 11-402.", "m"));
        ExternalLlmClient client = client(baseUrl());
        var history = List.of(ConversationMessage.user("What courses do I have?", java.time.Instant.EPOCH),
                ConversationMessage.assistant("1. CS301\n2. CSC452", java.time.Instant.EPOCH));
        var tool = new AcademicAiToolDefinition("get_my_course_section", "Section", Map.of("type", "object"));
        LlmResponse turn = client.chat(new LlmRequest(SYSTEM, "Where is the first one?", "req", List.of(tool), List.of(), false, history));
        JsonNode first = json.readTree(requests.take().body()).get("messages");
        assertThat(first.size()).isEqualTo(4);
        assertThat(first.get(1).get("role").stringValue()).isEqualTo("user");
        assertThat(first.get(1).get("content").stringValue()).isEqualTo("What courses do I have?");
        assertThat(first.get(2).get("role").stringValue()).isEqualTo("assistant");
        assertThat(first.get(2).has("tool_calls")).isFalse();
        assertThat(first.get(3).get("content").stringValue()).isEqualTo("Where is the first one?");

        var round = new LlmToolRound(turn, List.of(new LlmToolResult("call_1", "get_my_course_section", Map.of("success", true))));
        client.chat(new LlmRequest(SYSTEM, "Where is the first one?", "req", List.of(tool), List.of(round), false, history));
        JsonNode second = json.readTree(requests.take().body()).get("messages");
        // History, then the current question, then this request's own tool round with its thought signature intact.
        assertThat(second.size()).isEqualTo(6);
        assertThat(second.get(3).get("content").stringValue()).isEqualTo("Where is the first one?");
        assertThat(second.get(4).get("tool_calls").get(0).get("extra_content").get("google").get("thought_signature").stringValue())
                .isEqualTo("sig-abc");
        assertThat(second.get(5).get("role").stringValue()).isEqualTo("tool");
    }

    @Test
    void replaysTheProviderTurnUnchangedFollowedByToolResults() throws Exception {
        reply(200, TOOL_TURN);
        reply(200, success("Dr. John Smith teaches CS301.", "m"));
        ExternalLlmClient client = client(baseUrl());
        LlmResponse turn = client.chat(toolRequest(List.of()));
        requests.take();
        var round = new LlmToolRound(turn, List.of(
                new LlmToolResult("call_1", "get_my_course_lecturer", Map.of("success", true, "data", Map.of("lecturerNameEn", "Dr. John Smith"))),
                new LlmToolResult("call_2", "get_section_availability", Map.of("success", false, "code", "COURSE_SECTION_NOT_FOUND"))));

        assertThat(client.chat(toolRequest(List.of(round))).content()).isEqualTo("Dr. John Smith teaches CS301.");

        JsonNode messages = json.readTree(requests.take().body()).get("messages");
        assertThat(messages.size()).isEqualTo(5);
        JsonNode assistant = messages.get(2);
        assertThat(assistant.get("role").stringValue()).isEqualTo("assistant");
        // Gemini's thought signature survives the round trip; without it Gemini rejects the request with 400.
        assertThat(assistant.get("tool_calls").get(0).get("extra_content").get("google").get("thought_signature").stringValue())
                .isEqualTo("sig-abc");
        assertThat(messages.get(3).get("role").stringValue()).isEqualTo("tool");
        assertThat(messages.get(3).get("tool_call_id").stringValue()).isEqualTo("call_1");
        assertThat(json.readTree(messages.get(3).get("content").stringValue()).get("data").get("lecturerNameEn").stringValue())
                .isEqualTo("Dr. John Smith");
        assertThat(messages.get(4).get("tool_call_id").stringValue()).isEqualTo("call_2");
    }

    @Test
    void malformedToolArgumentsBecomeNullForTheExecutorToReject() {
        reply(200, "{\"choices\":[{\"message\":{\"tool_calls\":["
                + "{\"id\":\"a\",\"function\":{\"name\":\"get_my_courses\",\"arguments\":\"{not json\"}},"
                + "{\"id\":\"b\",\"function\":{\"name\":\"get_my_courses\",\"arguments\":\"[1,2]\"}},"
                + "{\"id\":\"c\",\"function\":{\"name\":\"get_my_courses\",\"arguments\":\"\"}},"
                + "{\"id\":\"d\",\"function\":{\"name\":\"delete_student\",\"arguments\":\"{\\\"studentId\\\":15}\"}}]}}]}");
        LlmResponse response = client(baseUrl()).chat(toolRequest(List.of()));
        assertThat(response.toolCalls().get(0).arguments()).isNull();
        assertThat(response.toolCalls().get(1).arguments()).isNull();
        assertThat(response.toolCalls().get(2).arguments()).isEmpty();
        // Unknown tool names are parsed as-is; the executor's allowlist rejects them.
        assertThat(response.toolCalls().get(3).name()).isEqualTo("delete_student");
    }

    @Test
    void toolCallsWithoutIdOrNameAreInvalidResponses() {
        for (String call : List.of("{\"function\":{\"name\":\"get_my_courses\",\"arguments\":\"{}\"}}",
                "{\"id\":\"a\",\"function\":{\"arguments\":\"{}\"}}", "{\"id\":\" \",\"function\":{\"name\":\"x\"}}")) {
            reply(200, "{\"choices\":[{\"message\":{\"tool_calls\":[" + call + "]}}]}");
            assertThatThrownBy(() -> client(baseUrl()).chat(toolRequest(List.of())))
                    .isInstanceOfSatisfying(AiAssistantFailure.class, e -> assertThat(e.code()).isEqualTo("INVALID_AI_PROVIDER_RESPONSE"));
        }
    }

    @Test
    void emptyToolCallListWithoutTextIsInvalid() {
        reply(200, "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[]}}]}");
        assertFailure(client(baseUrl()), 502, "INVALID_AI_PROVIDER_RESPONSE");
    }

    private ExternalLlmClient client(String baseUrl) { return client(baseUrl, 1); }
    private ExternalLlmClient client(String baseUrl, int retries) {
        return new ExternalLlmClient(properties(baseUrl, KEY, "configured-model", retries), json);
    }
    private static AiAssistantProperties properties(String baseUrl, String key, String model, int retries) {
        return new AiAssistantProperties("test-provider", key, model, baseUrl, 1, 1, 0.2, 512, retries, Duration.ZERO, 4, 8, 120);
    }
    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private static LlmRequest request() { return new LlmRequest(SYSTEM, "What is a credit hour?", "req"); }
    private void reply(int status, String body) { reply(status, body, 0); }
    private void reply(int status, String body, long delayMillis) { replies.add(new Reply(status, body, delayMillis)); }
    private static String success(String content, String model) {
        return "{\"id\":\"chatcmpl-123\",\"object\":\"chat.completion\",\"model\":\"" + model + "\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
    }
    private static String header(Recorded recorded, String name) {
        return recorded.headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(e -> e.getValue().getFirst()).findFirst().orElse(null);
    }
    private static <T> List<T> iterable(Iterable<T> values) {
        var list = new ArrayList<T>();
        values.forEach(list::add);
        return list;
    }
    private static void assertFailure(ExternalLlmClient client, int status, String code) {
        assertThatThrownBy(() -> client.chat(request())).isInstanceOfSatisfying(AiAssistantFailure.class, e -> {
            assertThat(e.status()).isEqualTo(status);
            assertThat(e.code()).isEqualTo(code);
        });
    }
}
