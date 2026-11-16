package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One retrievable passage of a file revision. The embedding column is not mapped on purpose: it is written and read by
 * native SQL only (spec files 50 to 52). embedding_model and embedded_at are read-only here because the database
 * requires them to change together with the vector.
 */
@Entity @Table(name = "academic_document_chunks") @Getter @Setter
public class AcademicDocumentChunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false) private AcademicDocumentFile file;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    @Column(name = "content", nullable = false, columnDefinition = "text") private String content;
    @Column(name = "section_title", length = 500) private String sectionTitle;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @Column(name = "char_count", nullable = false) private int charCount;
    @Column(name = "token_estimate", nullable = false) private int tokenEstimate;
    @Column(name = "page_start", nullable = false) private int pageStart;
    @Column(name = "page_end", nullable = false) private int pageEnd;
    @Column(name = "embedding_model", length = 100, insertable = false, updatable = false) private String embeddingModel;
    @Column(name = "embedded_at", insertable = false, updatable = false) private LocalDateTime embeddedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
