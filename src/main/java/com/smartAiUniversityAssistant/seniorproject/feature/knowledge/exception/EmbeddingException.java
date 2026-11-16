package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.exception;

/**
 * Thrown by an EmbeddingClient with a code of spec file 50 section 10 and whether a later attempt can succeed. The
 * message is a fixed safe text; it never contains provider text, the key, a URL or the embedded input.
 */
public class EmbeddingException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public EmbeddingException(String code) {
        super(message(code), null, false, false);
        this.code = code;
        this.retryable = switch (code) {
            case "EMBEDDING_RATE_LIMITED", "EMBEDDING_PROVIDER_UNAVAILABLE", "EMBEDDING_TIMEOUT", "EMBEDDING_INVALID_RESPONSE" -> true;
            default -> false;
        };
    }

    public String code() { return code; }
    public boolean retryable() { return retryable; }

    public static String message(String code) {
        return switch (code) {
            case "EMBEDDING_NOT_CONFIGURED" -> "The embedding model is not configured.";
            case "EMBEDDING_AUTHENTICATION_FAILED" -> "The embedding provider rejected the credentials.";
            case "EMBEDDING_REQUEST_REJECTED" -> "The embedding provider rejected the request.";
            case "EMBEDDING_RATE_LIMITED" -> "The embedding provider is rate limiting requests.";
            case "EMBEDDING_PROVIDER_UNAVAILABLE" -> "The embedding provider is temporarily unavailable.";
            case "EMBEDDING_TIMEOUT" -> "The embedding provider did not answer in time.";
            case "EMBEDDING_INVALID_RESPONSE" -> "The embedding provider returned an invalid response.";
            case "EMBEDDING_DIMENSION_MISMATCH" -> "The embedding provider returned vectors of an unexpected size.";
            default -> "The embedding request failed.";
        };
    }
}
