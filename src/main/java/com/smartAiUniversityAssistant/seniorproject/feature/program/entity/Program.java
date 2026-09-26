package com.smartAiUniversityAssistant.seniorproject.feature.program.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.department.entity.Department;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "programs") @Getter @Setter
public class Program {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "program_code", nullable = false, length = 50) private String programCode;
    @Column(name = "program_name", nullable = false, length = 255) private String programName;
    @Column(name = "degree_level", nullable = false, length = 100) private String degreeLevel;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false) private Department department;
    @Column(name = "duration_years") private Integer durationYears;
    @Column(name = "total_credits") private Integer totalCredits;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
