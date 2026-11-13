package com.smartAiUniversityAssistant.seniorproject.feature.assistant.exception;

/** Application-level assistant errors; messages are safe for students and never carry provider details. */
public class AiAssistantFailure extends RuntimeException {
    private static final String TRY_AGAIN = "The AI assistant is temporarily unavailable. Please try again.";
    private final int status;
    private final String code;

    public AiAssistantFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static AiAssistantFailure invalidMessage() {
        return new AiAssistantFailure(400, "VALIDATION_ERROR", "The request is invalid.");
    }
    public static AiAssistantFailure unavailable() {
        return new AiAssistantFailure(503, "AI_ASSISTANT_UNAVAILABLE", TRY_AGAIN);
    }
    public static AiAssistantFailure timeout() {
        return new AiAssistantFailure(503, "AI_REQUEST_TIMEOUT", TRY_AGAIN);
    }
    public static AiAssistantFailure rateLimited() {
        return new AiAssistantFailure(429, "AI_PROVIDER_RATE_LIMITED", "The AI assistant is busy. Please try again shortly.");
    }
    public static AiAssistantFailure invalidProviderResponse() {
        return new AiAssistantFailure(502, "INVALID_AI_PROVIDER_RESPONSE", TRY_AGAIN);
    }
    // The model kept asking for tools past AI_MAX_TOOL_ROUNDS / AI_MAX_TOOL_CALLS within one chat request.
    public static AiAssistantFailure toolCallLimitExceeded() {
        return new AiAssistantFailure(502, "AI_TOOL_CALL_LIMIT_EXCEEDED",
                "The AI assistant could not complete this request. Please try asking a more specific question.");
    }

    // Step 10 conversation context. A missing, expired and another student's conversation all look the same (404).
    public static AiAssistantFailure invalidConversationId() {
        return new AiAssistantFailure(400, "INVALID_CONVERSATION_ID", "The conversation id is invalid.");
    }
    public static AiAssistantFailure conversationNotFound() {
        return new AiAssistantFailure(404, "CONVERSATION_NOT_FOUND",
                "This conversation was not found or has expired. Send your message without a conversationId to start a new one.");
    }
    public static AiAssistantFailure conversationConflict() {
        return new AiAssistantFailure(409, "CONVERSATION_CONFLICT",
                "This conversation was updated by another request. Please send your message again.");
    }
    public static AiAssistantFailure contextUnavailable() {
        return new AiAssistantFailure(503, "ASSISTANT_CONTEXT_UNAVAILABLE",
                "The conversation could not be loaded or saved right now. Please try again.");
    }

    // Step 17: PostgreSQL could not store or restore the turn. Nothing of the turn is kept in the history.
    public static AiAssistantFailure historyUnavailable() {
        return new AiAssistantFailure(503, "ASSISTANT_HISTORY_UNAVAILABLE",
                "The conversation history could not be loaded or saved right now. Please try again.");
    }
}
