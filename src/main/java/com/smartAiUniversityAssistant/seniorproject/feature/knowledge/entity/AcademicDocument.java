package com.smartAiUniversityAssistant.seniorproject.feature.knowledge.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.entity.CurriculumVersion;
import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Logical knowledge document. At most one of curriculumVersion, program and department is set; none means the
 * document is university-wide. The program and department of a curriculum document are derived, never stored.
 */
@Entity @Table(name = "academic_documents") @Getter @Setter
public class AcademicDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "title", nullable = false, length = 255) private String title;
    @Column(name = "description", columnDefinition = "text") private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false) private DocumentCategory category;
    @Column(name = "academic_year_label", nullable = false, length = 20) private String academicYearLabel;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curriculum_version_id") private CurriculumVersion curriculumVersion;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id") private Program program;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id") private Department department;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
