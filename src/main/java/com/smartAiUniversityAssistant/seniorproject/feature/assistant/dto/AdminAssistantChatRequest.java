package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * Body of POST /api/v1/admin/assistant/chat (spec file 59). history is the earlier turns of the Admin's current chat,
 * oldest first, kept by the browser only: the backend stores nothing of an Admin chat.
 */
public record AdminAssistantChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 4000, message = "Message must not exceed 4000 characters")
        String message,
        @Size(max = 20) List<@NotNull @Valid Turn> history) {

    public AdminAssistantChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public record Turn(@NotNull @Pattern(regexp = "USER|ASSISTANT") String role, @NotBlank @Size(max = 8000) String content) {}
}
