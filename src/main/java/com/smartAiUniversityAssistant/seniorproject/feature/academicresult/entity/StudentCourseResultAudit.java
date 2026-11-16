package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "student_course_result_audits") @Getter @Setter
public class StudentCourseResultAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "student_course_result_id", nullable = false) private Long resultId;
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20) private CourseResultAuditAction action;
    @Column(name = "previous_grade_definition_id") private Long previousGradeDefinitionId;
    @Column(name = "new_grade_definition_id", nullable = false) private Long newGradeDefinitionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20) private CourseResultStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20) private CourseResultStatus newStatus;
    @Column(name = "credits_attempted", nullable = false, precision = 5, scale = 2) private BigDecimal creditsAttempted;
    @Column(name = "reason", length = 500) private String reason;
    @Column(name = "changed_by", nullable = false) private Long changedBy;
    @Column(name = "changed_at", nullable = false) private LocalDateTime changedAt;
}
