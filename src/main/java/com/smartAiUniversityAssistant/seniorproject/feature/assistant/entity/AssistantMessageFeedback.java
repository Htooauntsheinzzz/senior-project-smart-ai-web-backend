package com.smartAiUniversityAssistant.seniorproject.feature.assistant.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Spec file 59: a Student's rating of one assistant answer. rating is POSITIVE or NEGATIVE; reviewStatus is PENDING,
 * REVIEWED or RESOLVED. submittedAt is the time of the Student's latest rating or comment.
 */
@Entity @Table(name = "assistant_message_feedback") @Getter @Setter
public class AssistantMessageFeedback {
    public static final String POSITIVE = "POSITIVE";
    public static final String NEGATIVE = "NEGATIVE";
    public static final String PENDING = "PENDING";
    public static final String REVIEWED = "REVIEWED";
    public static final String RESOLVED = "RESOLVED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "message_id", nullable = false, updatable = false) private Long messageId;
    @Column(name = "conversation_id", nullable = false, updatable = false) private UUID conversationId;
    @Column(name = "student_id", nullable = false, updatable = false) private Long studentId;
    @Column(name = "rating", nullable = false, length = 20) private String rating;
    @Column(name = "comment", length = 1000) private String comment;
    @Column(name = "review_status", nullable = false, length = 20) private String reviewStatus;
    @Column(name = "review_note", length = 1000) private String reviewNote;
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "submitted_at", nullable = false) private Instant submittedAt;
}
