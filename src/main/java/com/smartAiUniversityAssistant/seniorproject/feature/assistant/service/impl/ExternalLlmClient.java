package com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.impl;

import com.smartAiUniversityAssistant.seniorproject.config.AiAssistantProperties;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception.AiAssistantFailure;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.service.LlmClient;
import java.net.SocketTimeoutException;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Calls a cloud LLM through the OpenAI-compatible Chat Completions API (POST {base-url}/chat/completions with a
 * bearer key), which OpenAI, Google Gemini's OpenAI endpoint, Groq, OpenRouter, Mistral and others accept, so the
 * provider is chosen by configuration. Approved tools are sent as OpenAI-style function declarations and the
 * model's tool_calls are parsed into provider-neutral {@link LlmToolCall}s. Provider JSON stays inside this class.
 * Only HTTP 429, 5xx and connection failures are retried, at most {@code max-retries} times; read timeouts and
 * other 4xx responses are not.
 * The API key, provider error bodies and the student's message are never logged.
 */
@Component
public class ExternalLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(ExternalLlmClient.class);
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final AiAssistantProperties properties;
    private final ObjectMapper json;
    private final RestClient http;

    public ExternalLlmClient(AiAssistantProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (!properties.configured()) {
            log.warn("AI_PROVIDER_NOT_CONFIGURED requestId={} (set AI_API_KEY, AI_MODEL and AI_BASE_URL)", request.requestId());
            throw AiAssistantFailure.unavailable();
        }
        byte[] body = requestBody(request);
        for (int attempt = 0; ; attempt++) {
            Attempt result = send(body, request.requestId());
            if (result.response() != null) return result.response();
            if (!result.retryable() || attempt >= properties.maxRetries()) throw result.failure();
            log.info("AI_PROVIDER_RETRY requestId={} attempt={} reason={}", request.requestId(), attempt + 2, result.failure().code());
            pause();
        }
    }

    private record Attempt(LlmResponse response, AiAssistantFailure failure, boolean retryable) {
        static Attempt success(LlmResponse response) { return new Attempt(response, null, false); }
        static Attempt failed(AiAssistantFailure failure, boolean retryable) { return new Attempt(null, failure, retryable); }
    }
    private record RawResponse(int status, byte[] body) {}

    private Attempt send(byte[] body, String requestId) {
        RawResponse raw;
        try {
            raw = http.post().uri(endpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey().strip())
                    .body(body)
                    .exchange((req, res) -> new RawResponse(res.getStatusCode().value(), res.getBody().readNBytes(MAX_RESPONSE_BYTES)), true);
        } catch (RuntimeException e) {
            if (isReadTimeout(e)) {
                log.warn("AI_PROVIDER_TIMEOUT requestId={} timeoutSeconds={}", requestId, properties.timeoutSeconds());
                return Attempt.failed(AiAssistantFailure.timeout(), false);
            }
            // Exception messages can echo the URL, so only the type is logged.
            log.warn("AI_PROVIDER_NETWORK_ERROR requestId={} error={}", requestId, rootCause(e).getClass().getSimpleName());
            return Attempt.failed(AiAssistantFailure.unavailable(), true);
        }

        int status = raw.status();
        if (status == 429) {
            log.warn("AI_PROVIDER_RATE_LIMITED requestId={} status=429", requestId);
            return Attempt.failed(AiAssistantFailure.rateLimited(), true);
        }
        if (status >= 500) {
            log.warn("AI_PROVIDER_ERROR requestId={} status={}", requestId, status);
            return Attempt.failed(AiAssistantFailure.unavailable(), true);
        }
        if (status == 401 || status == 403) {
            log.error("AI_PROVIDER_AUTHENTICATION_FAILURE requestId={} status={} (check AI_API_KEY)", requestId, status);
            return Attempt.failed(AiAssistantFailure.unavailable(), false);
        }
        if (status < 200 || status >= 300) {
            log.error("AI_PROVIDER_REJECTED_REQUEST requestId={} status={} (check AI_BASE_URL and AI_MODEL)", requestId, status);
            return Attempt.failed(AiAssistantFailure.unavailable(), false);
        }
        return parse(raw.body(), requestId);
    }

    private Attempt parse(byte[] body, String requestId) {
        try {
            JsonNode root = json.readTree(new String(body, StandardCharsets.UTF_8));
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode model = root.path("model");
            JsonNode id = root.path("id");
            String modelName = model.isString() && !model.stringValue().isBlank() ? model.stringValue() : properties.model();
            String providerId = id.isString() ? id.stringValue() : null;
            LlmUsage usage = usage(root.path("usage"));
            JsonNode content = message.path("content");
            String text = content.isString() && !content.stringValue().isBlank() ? content.stringValue() : null;

            JsonNode calls = message.path("tool_calls");
            if (calls.isArray() && !calls.isEmpty()) {
                var toolCalls = new ArrayList<LlmToolCall>();
                for (JsonNode call : calls) {
                    JsonNode callId = call.path("id");
                    JsonNode name = call.path("function").path("name");
                    if (!callId.isString() || callId.stringValue().isBlank() || !name.isString() || name.stringValue().isBlank()) {
                        log.warn("AI_PROVIDER_INVALID_RESPONSE requestId={} reason=malformed-tool-call", requestId);
                        return Attempt.failed(AiAssistantFailure.invalidProviderResponse(), false);
                    }
                    toolCalls.add(new LlmToolCall(callId.stringValue(), name.stringValue(),
                            arguments(call.path("function").path("arguments"))));
                }
                return Attempt.success(new LlmResponse(text, modelName, providerId, toolCalls, message, usage));
            }
            if (text == null) {
                log.warn("AI_PROVIDER_INVALID_RESPONSE requestId={} reason=missing-content", requestId);
                return Attempt.failed(AiAssistantFailure.invalidProviderResponse(), false);
            }
            return Attempt.success(new LlmResponse(text, modelName, providerId, List.of(), message, usage));
        } catch (JacksonException e) {
            log.warn("AI_PROVIDER_INVALID_RESPONSE requestId={} reason=malformed-json", requestId);
            return Attempt.failed(AiAssistantFailure.invalidProviderResponse(), false);
        }
    }

    // Token counts are optional in the response; a missing or malformed usage block means "not reported".
    private static LlmUsage usage(JsonNode usage) {
        if (!usage.isObject()) return null;
        return new LlmUsage(count(usage.path("prompt_tokens")), count(usage.path("completion_tokens")),
                count(usage.path("total_tokens")));
    }

    private static long count(JsonNode node) {
        return node.isNumber() && node.canConvertToLong() && node.asLong() >= 0 ? node.asLong() : 0;
    }

    // Arguments arrive as a JSON string. Anything that is not a JSON object becomes null, which the tool executor
    // rejects as INVALID_AI_TOOL_ARGUMENTS and reports back to the model instead of failing the whole request.
    @SuppressWarnings("unchecked")
    private Map<String, Object> arguments(JsonNode raw) {
        JsonNode node = raw;
        if (raw.isString()) {
            if (raw.stringValue().isBlank()) return Map.of();
            try {
                node = json.readTree(raw.stringValue());
            } catch (JacksonException e) {
                return null;
            }
        }
        if (node == null || !node.isObject()) return null;
        return json.convertValue(node, Map.class);
    }

    // System and user content travel as separate messages, so student text never becomes a system instruction.
    // Earlier conversation turns (Step 10) come first as plain user/assistant messages: they carry no tool calls, so
    // no thought signature is needed for them. Each tool round of the current request is replayed as the provider's
    // own assistant message followed by one tool message per call.
    private byte[] requestBody(LlmRequest request) {
        var messages = new ArrayList<Object>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        for (ConversationMessage earlier : request.history())
            messages.add(Map.of("role", ConversationMessage.ASSISTANT.equals(earlier.role()) ? "assistant" : "user",
                    "content", earlier.content()));
        messages.add(Map.of("role", "user", "content", request.userMessage()));
        for (LlmToolRound round : request.toolRounds()) {
            messages.add(assistantMessage(round.assistantTurn()));
            for (LlmToolResult result : round.results())
                messages.add(Map.of("role", "tool", "tool_call_id", result.callId(),
                        "content", json.writeValueAsString(result.result())));
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("model", properties.model().strip());
        payload.put("messages", messages);
        if (!request.tools().isEmpty()) {
            payload.put("tools", request.tools().stream().map(tool -> Map.of("type", "function", "function", Map.of(
                    "name", tool.name(), "description", tool.description(), "parameters", tool.parameterSchema()))).toList());
            // The grounding retry: the model must pick one of the declared tools instead of answering from memory.
            if (request.requireTool()) payload.put("tool_choice", "required");
        }
        payload.put("temperature", properties.temperature());
        payload.put("max_tokens", properties.maxOutputTokens());
        return json.writeValueAsBytes(payload);
    }

    // The provider's own message is sent back unchanged when available (it carries Gemini's thought signature).
    private static Object assistantMessage(LlmResponse turn) {
        if (turn.providerMessage() instanceof JsonNode node) return node;
        var calls = turn.toolCalls().stream().map(call -> Map.of("id", call.callId(), "type", "function",
                "function", Map.of("name", call.name(), "arguments", call.arguments() == null ? "{}" : call.arguments()))).toList();
        var message = new LinkedHashMap<String, Object>();
        message.put("role", "assistant");
        message.put("content", turn.content());
        message.put("tool_calls", calls);
        return message;
    }

    private String endpoint() {
        String base = properties.baseUrl().strip();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/chat/completions";
    }

    private void pause() {
        try {
            Thread.sleep(properties.retryBackoff());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw AiAssistantFailure.unavailable();
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
}
