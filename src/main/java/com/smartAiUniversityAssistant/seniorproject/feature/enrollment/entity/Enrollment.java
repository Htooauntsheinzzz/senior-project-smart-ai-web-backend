package com.smartAiUniversityAssistant.seniorproject.feature.enrollment.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import com.smartAiUniversityAssistant.seniorproject.feature.enrollment.enums.EnrollmentStatus;
import com.smartAiUniversityAssistant.seniorproject.feature.student.entity.Student;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "enrollments") @Getter @Setter
public class Enrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false) private Student student;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_section_id", nullable = false) private CourseSection courseSection;
    @Column(name = "enrollment_date", nullable = false) private LocalDate enrollmentDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30) private EnrollmentStatus status;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
