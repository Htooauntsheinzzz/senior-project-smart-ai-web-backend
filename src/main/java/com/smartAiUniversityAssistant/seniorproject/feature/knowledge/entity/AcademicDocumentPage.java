package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.knowledge.enums.DocumentUnitType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** The text of one unit of a file revision, as extracted (raw) and after the CLEAN stage (cleaned). */
@Entity @Table(name = "academic_document_pages") @Getter @Setter
public class AcademicDocumentPage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false) private AcademicDocumentFile file;
    @Column(name = "page_number", nullable = false) private int pageNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 20) private DocumentUnitType unitType;
    @Column(name = "unit_label", length = 255) private String unitLabel;
    @Column(name = "raw_text", nullable = false, columnDefinition = "text") private String rawText;
    @Column(name = "raw_char_count", nullable = false) private int rawCharCount;
    @Column(name = "cleaned_text", columnDefinition = "text") private String cleanedText;
    @Column(name = "cleaned_char_count") private Integer cleanedCharCount;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
}
