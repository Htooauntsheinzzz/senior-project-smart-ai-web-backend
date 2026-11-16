package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "document_categories") @Getter @Setter
public class DocumentCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "category_code", nullable = false, length = 60, unique = true) private String categoryCode;
    @Column(name = "category_name", nullable = false, length = 150) private String categoryName;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
