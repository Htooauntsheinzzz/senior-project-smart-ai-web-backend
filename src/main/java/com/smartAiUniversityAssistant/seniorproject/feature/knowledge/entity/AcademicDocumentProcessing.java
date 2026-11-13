package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentProcessingStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "academic_document_processing") @Getter @Setter
public class AcademicDocumentProcessing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, unique = true) private AcademicDocumentFile file;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) private DocumentProcessingStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "last_error_code", length = 100) private String lastErrorCode;
    @Column(name = "last_error_message", length = 500) private String lastErrorMessage;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    // Ingestion queue columns (spec file 46). Written by the worker and reprocess with conditional native updates.
    @Column(name = "failed_stage", length = 20) private String failedStage;
    @Column(name = "queued_at") private LocalDateTime queuedAt;
    @Column(name = "queued_by") private Long queuedBy;
    @Column(name = "next_attempt_at") private LocalDateTime nextAttemptAt;
}
