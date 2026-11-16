package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

/**
 * Token usage of one provider request as the provider reports it. promptTokens is everything sent (system prompt,
 * student message, tool declarations, earlier tool rounds); completionTokens is the generated answer or tool call;
 * totalTokens is the provider's own total, which for Gemini also includes its hidden thinking tokens.
 */
public record LlmUsage(long promptTokens, long completionTokens, long totalTokens) {
    public static final LlmUsage NONE = new LlmUsage(0, 0, 0);

    public LlmUsage plus(LlmUsage other) {
        return other == null ? this : new LlmUsage(promptTokens + other.promptTokens,
                completionTokens + other.completionTokens, totalTokens + other.totalTokens);
    }
}
