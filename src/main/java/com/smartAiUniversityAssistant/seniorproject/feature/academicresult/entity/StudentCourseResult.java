package com.smartAiUniversityAssistant.seniorproject.feature.academicresult.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.academicresult.enums.CourseResultStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity.Enrollment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// The Student is always derived through the Enrollment. creditsAttempted is frozen at finalization.
@Entity @Table(name = "student_course_results") @Getter @Setter
public class StudentCourseResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false) private Enrollment enrollment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_definition_id", nullable = false) private GradeDefinition gradeDefinition;
    @Column(name = "credits_attempted", nullable = false, precision = 5, scale = 2) private BigDecimal creditsAttempted;
    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 20) private CourseResultStatus status;
    @Column(name = "finalized_at") private LocalDateTime finalizedAt;
    @Column(name = "finalized_by") private Long finalizedBy;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
