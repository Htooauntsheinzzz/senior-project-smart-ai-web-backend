package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.AssignmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "course_assignments") @Getter @Setter
public class CourseAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_section_id", nullable = false) private CourseSection courseSection;
    @Column(name = "title", nullable = false, length = 255) private String title;
    @Column(name = "description", columnDefinition = "text") private String description;
    @Column(name = "due_at", nullable = false) private OffsetDateTime dueAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) private AssignmentStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
