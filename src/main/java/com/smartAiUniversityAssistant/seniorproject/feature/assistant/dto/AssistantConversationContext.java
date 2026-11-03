package com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;

/**
 * The Redis value at student:assistant:conversation:&lt;conversationId&gt;. studentId is the owner, taken from the
 * token when the conversation was created and never exposed. version counts saved turns (0 = not stored yet) and is
 * the optimistic-concurrency check for the next save.
 */
public record AssistantConversationContext(int schemaVersion, String conversationId, Long studentId, Instant createdAt,
        Instant updatedAt, long version, List<ConversationMessage> messages, AcademicConversationReference academicReference) {
    public static final int SCHEMA_VERSION = 1;

    public AssistantConversationContext {
        messages = messages == null ? List.of() : List.copyOf(messages);
        if (academicReference == null) academicReference = AcademicConversationReference.EMPTY;
    }

    @JsonIgnore
    public boolean isNew() { return version == 0; }
}
