package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One uploaded revision. The bytes live in protected storage under the backend-generated storageKey. */
@Entity @Table(name = "academic_document_files") @Getter @Setter
public class AcademicDocumentFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false) private AcademicDocument document;
    @Column(name = "revision_number", nullable = false) private int revisionNumber;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "storage_key", nullable = false, length = 600, unique = true) private String storageKey;
    @Column(name = "mime_type", nullable = false, length = 150) private String mimeType;
    @Column(name = "file_extension", nullable = false, length = 12) private String fileExtension;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256_checksum", nullable = false, length = 64) private String sha256Checksum;
    @Column(name = "is_current", nullable = false) private boolean current;
    @Column(name = "uploaded_by", nullable = false) private Long uploadedBy;
    @Column(name = "uploaded_at", nullable = false) private LocalDateTime uploadedAt;
}
