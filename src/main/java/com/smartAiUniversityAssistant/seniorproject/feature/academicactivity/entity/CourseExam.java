package com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.entity;

import com.smartAiUniversityAssistant.seniorproject.feature.academicactivity.enums.*;
import com.smartAiUniversityAssistant.seniorproject.feature.coursesection.entity.CourseSection;
import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name = "course_exams") @Getter @Setter
public class CourseExam {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_section_id", nullable = false) private CourseSection courseSection;
    @Column(name = "title", nullable = false, length = 255) private String title;
    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20) private ExamType examType;
    @Column(name = "starts_at", nullable = false) private OffsetDateTime startsAt;
    @Column(name = "ends_at", nullable = false) private OffsetDateTime endsAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20) private ExamStatus status;
    @Column(name = "room_id") private Long roomId;
    @Column(name = "room_label", length = 150) private String roomLabel;
    @Column(name = "is_deleted", nullable = false) private boolean deleted;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
