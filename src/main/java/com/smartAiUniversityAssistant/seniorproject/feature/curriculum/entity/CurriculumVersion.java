package com.smartAiUniversityAssistant.seniorproject.feature.curriculum.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.curriculum.enums.CurriculumStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.program.entity.Program;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "curriculum_versions") @Getter @Setter
public class CurriculumVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false) private Program program;
    @Column(name = "version_code", nullable = false, length = 50) private String versionCode;
    @Column(name = "version_name", nullable = false, length = 200) private String versionName;
    @Column(name = "academic_year_label", nullable = false, length = 20) private String academicYearLabel;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) private CurriculumStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
