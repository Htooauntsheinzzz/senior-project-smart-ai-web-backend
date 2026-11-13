package com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Step 17 (spec file 58): one stored message, the Student's text (USER) or the final answer (ASSISTANT). */
@Entity @Table(name = "assistant_messages") @Getter @Setter
public class AssistantMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "conversation_id", nullable = false, updatable = false) private UUID conversationId;
    @Column(name = "role", nullable = false, length = 20) private String role;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(name = "request_id", length = 36) private String requestId;
    @Column(name = "model", length = 100) private String model;
    @Column(name = "sources", columnDefinition = "text") private String sources;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
}
