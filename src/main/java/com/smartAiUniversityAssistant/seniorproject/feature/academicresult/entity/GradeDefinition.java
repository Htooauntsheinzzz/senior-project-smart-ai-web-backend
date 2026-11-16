package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "grade_definitions") @Getter @Setter
public class GradeDefinition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grading_scheme_id", nullable = false) private GradingScheme gradingScheme;
    @Column(name = "grade_code", nullable = false, length = 20) private String gradeCode;
    @Column(name = "grade_point", precision = 4, scale = 2) private BigDecimal gradePoint;
    @Column(name = "counts_in_gpa", nullable = false) private boolean countsInGpa;
    @Column(name = "earns_credit", nullable = false) private boolean earnsCredit;
    @Column(name = "is_passing", nullable = false) private boolean passing;
    @Column(name = "is_active", nullable = false) private boolean active;
}
