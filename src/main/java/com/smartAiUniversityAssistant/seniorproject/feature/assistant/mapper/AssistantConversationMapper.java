package com.smartAiUniversityAssistant.seniorproject.feature.assistant.mapper;

import com.smartAiUniversityAssistant.seniorproject.feature.assistant.dto.*;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantConversation;
import com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity.AssistantMessage;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Step 17 (spec file 58): stored conversations to API responses, and the JSON text columns (sources, academic
 * reference). A stored value that cannot be read becomes "no sources" or the empty reference, never an error.
 */
@Component
public class AssistantConversationMapper {
    public static final int TITLE_MAX_CHARS = 80;
    public static final int PREVIEW_MAX_CHARS = 160;
    private static final String ELLIPSIS = "…";
    private static final TypeReference<List<StudentAssistantChatResponse.Source>> SOURCES = new TypeReference<>() {};

    private final ObjectMapper json;

    public AssistantConversationMapper(ObjectMapper json) { this.json = json; }

    public AssistantConversationSummaryResponse toSummary(AssistantConversation c) {
        return new AssistantConversationSummaryResponse(c.getId().toString(), c.getTitle(), c.getLastMessagePreview(),
                c.getMessageCount(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public AssistantConversationDetailResponse toDetail(AssistantConversation c, List<AssistantMessage> window, boolean hasMoreBefore) {
        return new AssistantConversationDetailResponse(c.getId().toString(), c.getTitle(), c.getMessageCount(), c.getCreatedAt(),
                c.getUpdatedAt(), hasMoreBefore, window.stream().map(this::toMessage).toList());
    }

    public AssistantMessageResponse toMessage(AssistantMessage m) {
        return new AssistantMessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt(), sources(m.getSources()));
    }

    /** Null when there is no source, so the column stays NULL. */
    public String sourcesJson(List<StudentAssistantChatResponse.Source> sources) {
        return sources == null || sources.isEmpty() ? null : json.writeValueAsString(sources);
    }

    public String referenceJson(AcademicConversationReference reference) {
        return reference == null ? null : json.writeValueAsString(reference);
    }

    public AcademicConversationReference reference(String stored) {
        if (stored == null || stored.isBlank()) return AcademicConversationReference.EMPTY;
        try {
            var reference = json.readValue(stored, AcademicConversationReference.class);
            return reference == null ? AcademicConversationReference.EMPTY : reference;
        } catch (JacksonException unreadable) {
            return AcademicConversationReference.EMPTY;
        }
    }

    /** The first question as the conversation title: whitespace collapsed, at most TITLE_MAX_CHARS characters. */
    public static String title(String firstMessage) {
        String title = shorten(firstMessage, TITLE_MAX_CHARS);
        return title.isEmpty() ? "New conversation" : title;
    }

    public static String preview(String answer) { return shorten(answer, PREVIEW_MAX_CHARS); }

    private List<StudentAssistantChatResponse.Source> sources(String stored) {
        if (stored == null || stored.isBlank()) return List.of();
        try {
            var sources = json.readValue(stored, SOURCES);
            return sources == null ? List.of() : List.copyOf(sources);
        } catch (JacksonException | NullPointerException unreadable) {
            return List.of();
        }
    }

    private static String shorten(String text, int maxChars) {
        String collapsed = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (collapsed.length() <= maxChars) return collapsed;
        int end = maxChars - ELLIPSIS.length();
        if (Character.isHighSurrogate(collapsed.charAt(end - 1))) end--;
        return collapsed.substring(0, end).stripTrailing() + ELLIPSIS;
    }
}
