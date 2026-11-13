package com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Step 17 (spec file 58): the stored record of one assistant conversation. id is the conversationId of the chat
 * endpoint and of the Redis key; studentId is the owner from the token that started it.
 */
@Entity @Table(name = "assistant_conversations") @Getter @Setter
public class AssistantConversation {
    @Id private UUID id;
    @Column(name = "student_id", nullable = false, updatable = false) private Long studentId;
    @Column(name = "title", nullable = false, length = 120) private String title;
    @Column(name = "last_message_preview", length = 200) private String lastMessagePreview;
    @Column(name = "message_count", nullable = false) private int messageCount;
    @Column(name = "context_version", nullable = false) private long contextVersion;
    @Column(name = "academic_reference", columnDefinition = "text") private String academicReference;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
